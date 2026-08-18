// Package nodeagent implements the Gateway Node Agent (detail.md §1/§3.4):
// it samples node health/capacity and reports it to the control plane via
// POST /node/v1/heartbeat, authenticated by the per-node shared secret.
package nodeagent

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"runtime"
	"time"
)

// Options configures the node agent.
type Options struct {
	ServerURL      string
	Token          string
	FrpsAdminURL   string // optional; when set, proxy counts are sampled
	HeartbeatEvery time.Duration
}

// Agent is the node-agent runtime loop.
type Agent struct {
	opts Options
	log  *slog.Logger
	http *http.Client
}

func New(opts Options, log *slog.Logger) *Agent {
	return &Agent{
		opts: opts,
		log:  log,
		http: &http.Client{Timeout: 10 * time.Second},
	}
}

// Run blocks until ctx is cancelled, heartbeating on the configured interval.
func (a *Agent) Run(ctx context.Context) error {
	a.log.Info("node agent starting", "server", a.opts.ServerURL, "heartbeat", a.opts.HeartbeatEvery.String())
	ticker := time.NewTicker(a.opts.HeartbeatEvery)
	defer ticker.Stop()

	a.beat(ctx) // immediate first heartbeat
	for {
		select {
		case <-ctx.Done():
			a.log.Info("node agent stopping")
			return nil
		case <-ticker.C:
			a.beat(ctx)
		}
	}
}

func (a *Agent) beat(ctx context.Context) {
	metrics := a.collect(ctx)
	body, err := json.Marshal(map[string]any{"metrics": metrics})
	if err != nil {
		a.log.Error("marshal metrics", "err", err)
		return
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		a.opts.ServerURL+"/node/v1/heartbeat", bytes.NewReader(body))
	if err != nil {
		a.log.Error("build heartbeat", "err", err)
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+a.opts.Token)

	resp, err := a.http.Do(req)
	if err != nil {
		a.log.Warn("heartbeat failed", "err", err)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		a.log.Warn("heartbeat rejected", "status", resp.StatusCode, "body", string(b))
		return
	}
	a.log.Info("heartbeat ok", "proxies", metrics["frpsProxies"])
}

// collect samples node health/capacity. OS-specific sources are guarded so the
// binary still builds (and reports basics) on every platform.
func (a *Agent) collect(ctx context.Context) map[string]any {
	m := map[string]any{
		"hostname":   hostname(),
		"os":         runtime.GOOS,
		"arch":       runtime.GOARCH,
		"cpuCount":   runtime.NumCPU(),
		"goroutines": runtime.NumGoroutine(),
		"reportedAt": time.Now().UTC().Format(time.RFC3339),
	}
	if la := loadAvg(); la != nil {
		m["loadAvg1"] = la[0]
		m["loadAvg5"] = la[1]
		m["loadAvg15"] = la[2]
	}
	if total, free, ok := memInfo(); ok {
		m["memTotalBytes"] = total
		m["memFreeBytes"] = free
	}
	if total, free, ok := diskInfo("/"); ok {
		m["diskTotalBytes"] = total
		m["diskFreeBytes"] = free
	}
	if a.opts.FrpsAdminURL != "" {
		if n, ok := a.frpsProxyCount(ctx); ok {
			m["frpsProxies"] = n
			m["frpsReachable"] = true
		} else {
			m["frpsReachable"] = false
		}
	}
	return m
}

// frpsProxyCount asks the frps admin API how many proxies are currently
// registered (tcp + udp). detail.md §1: the node agent reports capacity.
func (a *Agent) frpsProxyCount(ctx context.Context) (int, bool) {
	total := 0
	for _, typ := range []string{"tcp", "udp"} {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet,
			fmt.Sprintf("%s/api/proxy/%s", a.opts.FrpsAdminURL, typ), nil)
		if err != nil {
			return 0, false
		}
		resp, err := a.http.Do(req)
		if err != nil {
			return 0, false
		}
		var payload struct {
			Proxies []json.RawMessage `json:"proxies"`
		}
		err = json.NewDecoder(resp.Body).Decode(&payload)
		resp.Body.Close()
		if err != nil {
			return 0, false
		}
		total += len(payload.Proxies)
	}
	return total, true
}

func hostname() string {
	h, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return h
}
