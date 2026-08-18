// duox-node-agent is the gateway-side agent binary (detail.md §1/§3.4/§16):
// it runs on the machine hosting frps, reports node health/capacity metrics
// to the control plane on a heartbeat, and authenticates with the per-node
// shared secret issued at node registration. It is deliberately a separate
// binary from duox-agent (different privilege/deployment model, §16).
package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/duox/dtunnel-agent/nodeagent"
)

func main() {
	serverURL := flag.String("server", envOr("DUOX_SERVER", "http://localhost:8080"), "control plane base URL")
	token := flag.String("token", os.Getenv("DUOX_NODE_TOKEN"), "node token issued at registration")
	heartbeat := flag.Duration("heartbeat", 30*time.Second, "heartbeat interval")
	frpsAdmin := flag.String("frps-admin", os.Getenv("DUOX_FRPS_ADMIN"), "frps admin API base URL (optional, e.g. http://127.0.0.1:7500)")
	flag.Parse()

	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if *token == "" {
		log.Error("node token required (--token or DUOX_NODE_TOKEN)")
		os.Exit(2)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	na := nodeagent.New(nodeagent.Options{
		ServerURL:      *serverURL,
		Token:          *token,
		FrpsAdminURL:   *frpsAdmin,
		HeartbeatEvery: *heartbeat,
	}, log)

	if err := na.Run(ctx); err != nil {
		log.Error("node agent exited", "err", err)
		os.Exit(1)
	}
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
