// Package control implements detail.md §4: the agent↔control-plane channel
// sits behind the AgentTransport interface so swapping REST for gRPC
// streaming in Phase 2 touches this package only.
package control

import "context"

// Proxy is one desired tunnel as delivered by the control plane.
type Proxy struct {
	TunnelID           string `json:"tunnelId"`
	Name               string `json:"name"`
	Type               string `json:"type"` // tcp | udp
	ServerAddr         string `json:"serverAddr"`
	ServerPort         int    `json:"serverPort"`
	RemotePort         int    `json:"remotePort"`
	LocalHost          string `json:"localHost"`
	LocalPort          int    `json:"localPort"`
	BandwidthLimitMbps int    `json:"bandwidthLimitMbps,omitempty"`
}

// DesiredState is the full /agent/v1/config response.
type DesiredState struct {
	Version int `json:"version"`
	Payload struct {
		AgentID string  `json:"agentId"`
		Proxies []Proxy `json:"proxies"`
	} `json:"payload"`
}

// TunnelReport is the agent's observed per-tunnel state for heartbeats.
// Usage metering is collected server-side from frps (detail.md Milestone 3.3).
type TunnelReport struct {
	TunnelID string `json:"tunnelId"`
	Status   string `json:"status"` // RUNNING | STOPPED | ERROR
}

// AgentTransport abstracts the channel (REST now, gRPC later — §4).
type AgentTransport interface {
	// Register performs first-run device registration (§6) and stores the
	// issued short-lived token for subsequent calls.
	Register(ctx context.Context, email, password, publicKey, platform, agentVersion string) (agentID string, err error)
	// ConfigVersion is the cheap poll (§8).
	ConfigVersion(ctx context.Context) (version int, agentStatus string, err error)
	// Config fetches the full desired state (§8).
	Config(ctx context.Context) (*DesiredState, error)
	// Heartbeat reports liveness + observed tunnel states (§8, §11).
	Heartbeat(ctx context.Context, appliedVersion int, agentVersion string, tunnels []TunnelReport) (desiredVersion int, err error)
}
