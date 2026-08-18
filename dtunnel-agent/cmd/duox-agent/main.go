// duox-agent is the user-side agent binary (detail.md §3.4): it wraps frpc,
// does control-plane auth/config-sync/heartbeat. The gateway-side
// duox-node-agent is a separate binary (detail.md §16 decision: separate
// privilege/deployment model) — not yet implemented.
package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/duox/dtunnel-agent/runtime"
)

func main() {
	serverURL := flag.String("server", envOr("DUOX_SERVER", "http://localhost:8080"), "control plane base URL")
	email := flag.String("email", "", "account email (first-run registration)")
	password := flag.String("password", "", "account password (first-run registration)")
	frpcPath := flag.String("frpc", envOr("DUOX_FRPC", "frpc"), "path to frpc executable")
	heartbeat := flag.Duration("heartbeat", 15*time.Second, "heartbeat interval (detail.md §4: 15-20s)")
	poll := flag.Duration("poll", 10*time.Second, "config version poll interval")
	flag.Parse()

	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	rt := runtime.New(runtime.Options{
		ServerURL:      *serverURL,
		Email:          *email,
		Password:       *password,
		FrpcPath:       *frpcPath,
		HeartbeatEvery: *heartbeat,
		PollEvery:      *poll,
	}, log)

	if err := rt.Run(ctx); err != nil {
		log.Error("agent exited", "err", err)
		os.Exit(1)
	}
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
