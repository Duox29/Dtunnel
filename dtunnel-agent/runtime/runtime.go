// Package runtime is the agent's reconcile loop (detail.md §12): it polls the
// desired state, supervises the local frpc process, and reports observed
// tunnel states back via heartbeat. Server is authoritative (§1): the agent
// only executes approved tunnel state.
package runtime

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sync"
	"time"

	"github.com/duox/dtunnel-agent/control"
	"github.com/duox/dtunnel-agent/frp"
)

const Version = "0.1.0"

type Options struct {
	ServerURL      string
	Email          string // first-run registration only
	Password       string // first-run registration only
	FrpcPath       string
	HeartbeatEvery time.Duration
	PollEvery      time.Duration
}

type state struct {
	AgentID        string `json:"agent_id"`
	Token          string `json:"token"`
	AppliedVersion int    `json:"applied_version"`
}

// Runtime drives the poll/heartbeat loop and frpc supervision.
type Runtime struct {
	opts    Options
	log     *slog.Logger
	rest    *control.RESTTransport
	adapter *frp.Adapter

	mu      sync.Mutex
	current *control.DesiredState
	frpcCmd *exec.Cmd
}

func New(opts Options, log *slog.Logger) *Runtime {
	return &Runtime{
		opts:    opts,
		log:     log,
		rest:    control.NewRESTTransport(opts.ServerURL),
		adapter: frp.NewAdapter(opts.FrpcPath, workDir()),
	}
}

func workDir() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "duox-agent", "frpc")
}

func statePath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "duox-agent", "state.json")
}

func (r *Runtime) loadState() state {
	var s state
	if data, err := os.ReadFile(statePath()); err == nil {
		_ = json.Unmarshal(data, &s)
	}
	return s
}

func (r *Runtime) saveState(s state) {
	data, _ := json.MarshalIndent(s, "", "  ")
	_ = os.WriteFile(statePath(), data, 0o600)
}

// Run blocks until ctx is cancelled.
func (r *Runtime) Run(ctx context.Context) error {
	st := r.loadState()

	if st.Token == "" {
		if r.opts.Email == "" || r.opts.Password == "" {
			return fmt.Errorf("not registered: run with --register --email --password first")
		}
		pubKey, err := r.devicePublicKey()
		if err != nil {
			return err
		}
		agentID, err := r.rest.Register(ctx, r.opts.Email, r.opts.Password, pubKey, runtime.GOOS, Version)
		if err != nil {
			return fmt.Errorf("registration failed: %w", err)
		}
		st.AgentID = agentID
		r.mu.Lock()
		token := r.token()
		r.mu.Unlock()
		st.Token = token
		r.saveState(st)
		r.log.Info("registered", "agentId", agentID)
	} else {
		r.rest.SetToken(st.Token)
	}

	heartbeat := time.NewTicker(r.opts.HeartbeatEvery)
	poll := time.NewTicker(r.opts.PollEvery)
	defer heartbeat.Stop()
	defer poll.Stop()

	// initial sync
	r.syncIfChanged(ctx, &st)

	for {
		select {
		case <-ctx.Done():
			r.stopFrpc()
			return nil
		case <-poll.C:
			r.syncIfChanged(ctx, &st)
		case <-heartbeat.C:
			r.heartbeat(ctx, &st)
		}
	}
}

func (r *Runtime) token() string {
	// RESTTransport keeps the issued token internally after Register
	return r.rest.Token()
}

func (r *Runtime) devicePublicKey() (string, error) {
	kp, err := identityLoad()
	if err != nil {
		return "", err
	}
	return kp.PublicKeyB64(), nil
}

func (r *Runtime) syncIfChanged(ctx context.Context, st *state) {
	version, agentStatus, err := r.rest.ConfigVersion(ctx)
	if err != nil {
		r.log.Warn("config version poll failed", "err", err)
		return
	}
	if agentStatus == "REVOKED" {
		r.log.Error("agent revoked by control plane; stopping tunnels")
		r.applyDesired(ctx, st, &control.DesiredState{})
		return
	}
	if agentStatus == "PENDING" {
		r.log.Info("agent awaiting SUPERADMIN approval")
		return
	}
	if version == st.AppliedVersion && r.current != nil {
		return
	}
	desired, err := r.rest.Config(ctx)
	if err != nil {
		r.log.Warn("config fetch failed", "err", err)
		return
	}
	r.applyDesired(ctx, st, desired)
}

func (r *Runtime) applyDesired(ctx context.Context, st *state, desired *control.DesiredState) {
	r.mu.Lock()
	r.current = desired
	r.mu.Unlock()

	r.stopFrpc()

	if len(desired.Payload.Proxies) > 0 {
		cfg, err := r.adapter.RenderConfig(desired.Payload.AgentID, st.Token, desired.Payload.Proxies)
		if err != nil {
			r.log.Error("render frpc config failed", "err", err)
			return
		}
		cmd := r.adapter.Command(cfg)
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err := cmd.Start(); err != nil {
			r.log.Error("start frpc failed", "err", err)
			return
		}
		r.mu.Lock()
		r.frpcCmd = cmd
		r.mu.Unlock()
		r.log.Info("frpc started", "proxies", len(desired.Payload.Proxies), "version", desired.Version)
	} else {
		r.log.Info("no desired proxies; frpc stopped")
	}

	st.AppliedVersion = desired.Version
	r.saveState(*st)
}

func (r *Runtime) stopFrpc() {
	r.mu.Lock()
	cmd := r.frpcCmd
	r.frpcCmd = nil
	r.mu.Unlock()
	if cmd != nil && cmd.Process != nil {
		_ = cmd.Process.Kill()
		_, _ = cmd.Process.Wait()
	}
}

func (r *Runtime) heartbeat(ctx context.Context, st *state) {
	r.mu.Lock()
	current := r.current
	cmd := r.frpcCmd
	r.mu.Unlock()

	var reports []control.TunnelReport
	if current != nil {
		frpcAlive := cmd != nil && cmd.ProcessState == nil
		for _, p := range current.Payload.Proxies {
			status := "STOPPED"
			if frpcAlive {
				status = "RUNNING"
			}
			reports = append(reports, control.TunnelReport{TunnelID: p.TunnelID, Status: status})
		}
	}
	desiredVersion, err := r.rest.Heartbeat(ctx, st.AppliedVersion, Version, reports)
	if err != nil {
		r.log.Warn("heartbeat failed", "err", err)
		return
	}
	if desiredVersion != st.AppliedVersion {
		r.syncIfChanged(ctx, st)
	}
}
