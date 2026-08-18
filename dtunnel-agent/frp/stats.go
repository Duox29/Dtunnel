package frp

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// ProxyStats is the per-proxy traffic sample read from frpc's admin API
// (GET /api/proxies). frpc reports cumulative counters since start.
type ProxyStats struct {
	Name       string
	TrafficIn  int64
	TrafficOut int64
	Status     string
}

// StatsClient queries the local frpc admin server.
type StatsClient struct {
	BaseURL string
	Client  *http.Client
}

func NewStatsClient() *StatsClient {
	return &StatsClient{
		BaseURL: "http://127.0.0.1:7400",
		Client:  &http.Client{Timeout: 3 * time.Second},
	}
}

// Proxies returns current per-proxy traffic counters.
func (s *StatsClient) Proxies(ctx context.Context) ([]ProxyStats, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.BaseURL+"/api/proxies", nil)
	if err != nil {
		return nil, err
	}
	resp, err := s.Client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("frpc admin: status %d", resp.StatusCode)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, err
	}
	var payload struct {
		Proxies []struct {
			Name    string `json:"name"`
			Status  string `json:"status"`
			Traffic struct {
				In  int64 `json:"in"`
				Out int64 `json:"out"`
			} `json:"traffic"`
		} `json:"proxies"`
	}
	if err := json.Unmarshal(data, &payload); err != nil {
		return nil, err
	}
	out := make([]ProxyStats, 0, len(payload.Proxies))
	for _, p := range payload.Proxies {
		out = append(out, ProxyStats{
			Name:       p.Name,
			TrafficIn:  p.Traffic.In,
			TrafficOut: p.Traffic.Out,
			Status:     p.Status,
		})
	}
	return out, nil
}
