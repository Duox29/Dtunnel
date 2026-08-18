// Package control: gRPC transport for the agent channel (detail.md §4
// Phase 2). REST remains the fallback; gRPC adds push-config and sub-second
// revocation over one bidirectional Control stream.
package control

import (
	"context"

	pb "github.com/duox/dtunnel-agent/control/pb"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// GRPCTransport talks to the control plane's gRPC agent channel.
type GRPCTransport struct {
	conn   *grpc.ClientConn
	client pb.AgentServiceClient
}

// NewGRPCTransport dials the gRPC agent channel (e.g. "localhost:9090").
// Local/dev uses plaintext; production should swap in TLS credentials.
func NewGRPCTransport(target string) (*GRPCTransport, error) {
	conn, err := grpc.NewClient(target, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return nil, err
	}
	return &GRPCTransport{conn: conn, client: pb.NewAgentServiceClient(conn)}, nil
}

func (g *GRPCTransport) Close() error { return g.conn.Close() }

// Register performs first-run device registration over gRPC (§6).
func (g *GRPCTransport) Register(ctx context.Context, email, password, publicKey, platform, agentVersion string) (agentID, token string, err error) {
	resp, err := g.client.Register(ctx, &pb.RegisterRequest{
		Email: email, Password: password, PublicKey: publicKey,
		Platform: platform, AgentVersion: agentVersion,
	})
	if err != nil {
		return "", "", err
	}
	return resp.GetAgentId(), resp.GetToken(), nil
}

// StreamEvent is one server->agent message on the Control stream.
type StreamEvent struct {
	Config     *DesiredState // set on ConfigPush
	Revoked    bool          // set on Revoked
	AckVersion int           // set on HeartbeatAck (-1 otherwise)
}

// ControlStream wraps the open bidirectional Control stream.
type ControlStream struct {
	stream pb.AgentService_ControlClient
	events chan StreamEvent
	err    chan error
}

// OpenControl opens the Control stream and sends the Hello handshake.
// Incoming messages are decoded into the Events channel until the stream
// breaks (signaled on Err).
func (g *GRPCTransport) OpenControl(ctx context.Context, token, agentVersion string, appliedVersion int) (*ControlStream, error) {
	stream, err := g.client.Control(ctx)
	if err != nil {
		return nil, err
	}
	if err := stream.Send(&pb.AgentMessage{
		Kind: &pb.AgentMessage_Hello{Hello: &pb.Hello{
			Token: token, AgentVersion: agentVersion, AppliedVersion: int32(appliedVersion),
		}},
	}); err != nil {
		return nil, err
	}
	cs := &ControlStream{
		stream: stream,
		events: make(chan StreamEvent, 8),
		err:    make(chan error, 1),
	}
	go cs.recvLoop()
	return cs, nil
}

func (cs *ControlStream) recvLoop() {
	for {
		msg, err := cs.stream.Recv()
		if err != nil {
			cs.err <- err
			return
		}
		switch k := msg.GetKind().(type) {
		case *pb.ServerMessage_Config:
			cs.events <- StreamEvent{Config: convertConfig(k.Config), AckVersion: -1}
		case *pb.ServerMessage_Revoked:
			cs.events <- StreamEvent{Revoked: true, AckVersion: -1}
		case *pb.ServerMessage_Ack:
			cs.events <- StreamEvent{AckVersion: int(k.Ack.GetDesiredVersion())}
		}
	}
}

// Events returns the channel of decoded server messages.
func (cs *ControlStream) Events() <-chan StreamEvent { return cs.events }

// Err returns the channel that receives the stream-breaking error.
func (cs *ControlStream) Err() <-chan error { return cs.err }

// SendHeartbeat reports liveness + observed tunnel states over the stream.
func (cs *ControlStream) SendHeartbeat(appliedVersion int, agentVersion string, tunnels []TunnelReport) error {
	reports := make([]*pb.TunnelReport, 0, len(tunnels))
	for _, t := range tunnels {
		reports = append(reports, &pb.TunnelReport{TunnelId: t.TunnelID, Status: t.Status})
	}
	return cs.stream.Send(&pb.AgentMessage{
		Kind: &pb.AgentMessage_Heartbeat{Heartbeat: &pb.Heartbeat{
			AppliedVersion: int32(appliedVersion),
			AgentVersion:   agentVersion,
			Tunnels:        reports,
		}},
	})
}

func convertConfig(c *pb.ConfigPush) *DesiredState {
	ds := &DesiredState{Version: int(c.GetVersion())}
	ds.Payload.Proxies = make([]Proxy, 0, len(c.GetProxies()))
	for _, p := range c.GetProxies() {
		ds.Payload.Proxies = append(ds.Payload.Proxies, Proxy{
			TunnelID:           p.GetTunnelId(),
			Name:               p.GetName(),
			Type:               p.GetType(),
			ServerAddr:         p.GetServerAddr(),
			ServerPort:         int(p.GetServerPort()),
			RemotePort:         int(p.GetRemotePort()),
			Domain:             p.GetDomain(),
			LocalHost:          p.GetLocalHost(),
			LocalPort:          int(p.GetLocalPort()),
			BandwidthLimitMbps: int(p.GetBandwidthLimitMbps()),
		})
	}
	return ds
}
