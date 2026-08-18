package control

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

// RESTTransport is the MVP AgentTransport implementation (detail.md §4):
// plain REST + short heartbeat, isolated behind the interface so a Phase 2
// gRPC streaming swap touches only this package.
type RESTTransport struct {
	BaseURL string
	Client  *http.Client

	mu    sync.RWMutex
	token string
}

func NewRESTTransport(baseURL string) *RESTTransport {
	return &RESTTransport{
		BaseURL: baseURL,
		Client:  &http.Client{Timeout: 15 * time.Second},
	}
}

// SetToken restores a previously issued device token (persisted by the runtime).
func (r *RESTTransport) SetToken(token string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.token = token
}

// Token returns the current device token (for persistence by the runtime).
func (r *RESTTransport) Token() string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.token
}

func (r *RESTTransport) do(ctx context.Context, method, path string, body any, authed bool, out any) error {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(data)
	}
	req, err := http.NewRequestWithContext(ctx, method, r.BaseURL+path, reader)
	if err != nil {
		return err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if authed {
		r.mu.RLock()
		token := r.token
		r.mu.RUnlock()
		if token == "" {
			return fmt.Errorf("not registered: no device token")
		}
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := r.Client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode >= 400 {
		return fmt.Errorf("%s %s: %d %s", method, path, resp.StatusCode, string(data))
	}
	if out != nil {
		return json.Unmarshal(data, out)
	}
	return nil
}

func (r *RESTTransport) Register(ctx context.Context, email, password, publicKey, platform, agentVersion string) (string, error) {
	body := map[string]string{
		"email":        email,
		"password":     password,
		"publicKey":    publicKey,
		"platform":     platform,
		"agentVersion": agentVersion,
	}
	var out struct {
		AgentID string `json:"agentId"`
		Token   string `json:"token"`
		Status  string `json:"status"`
	}
	if err := r.do(ctx, http.MethodPost, "/agent/v1/register", body, false, &out); err != nil {
		return "", err
	}
	r.mu.Lock()
	r.token = out.Token
	r.mu.Unlock()
	return out.AgentID, nil
}

func (r *RESTTransport) ConfigVersion(ctx context.Context) (int, string, error) {
	var out struct {
		Version int    `json:"version"`
		Status  string `json:"status"`
	}
	if err := r.do(ctx, http.MethodGet, "/agent/v1/config/version", nil, true, &out); err != nil {
		return 0, "", err
	}
	return out.Version, out.Status, nil
}

func (r *RESTTransport) Config(ctx context.Context) (*DesiredState, error) {
	var out DesiredState
	if err := r.do(ctx, http.MethodGet, "/agent/v1/config", nil, true, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

func (r *RESTTransport) Heartbeat(ctx context.Context, appliedVersion int, agentVersion string, tunnels []TunnelReport) (int, error) {
	body := map[string]any{
		"appliedVersion": appliedVersion,
		"agentVersion":   agentVersion,
		"tunnels":        tunnels,
	}
	var out struct {
		DesiredVersion int `json:"desiredVersion"`
	}
	if err := r.do(ctx, http.MethodPost, "/agent/v1/heartbeat", body, true, &out); err != nil {
		return 0, err
	}
	return out.DesiredVersion, nil
}
