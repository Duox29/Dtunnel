// Package frp is the adapter from detail.md §12: it translates a control.Proxy
// (domain tunnel) into an frpc proxy definition and supervises the local frpc
// process. It implements NO authorization — that lives entirely server-side
// (§9), which is what keeps a leaked agent credential from claiming arbitrary
// ports.
package frp

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/duox/dtunnel-agent/control"
)

// Adapter renders frpc TOML config and runs the frpc binary.
type Adapter struct {
	FrpcPath string // path to the frpc executable
	WorkDir  string // where rendered config files live
}

func NewAdapter(frpcPath, workDir string) *Adapter {
	return &Adapter{FrpcPath: frpcPath, WorkDir: workDir}
}

// RenderConfig writes one frpc TOML file covering all desired proxies.
// The frps server-plugin endpoint authorizes each proxy on connect (§9), so
// the client config carries identity (user field) but no secrets beyond the
// device token the control plane already issued to this machine.
func (a *Adapter) RenderConfig(agentID, deviceToken string, proxies []control.Proxy) (string, error) {
	if err := os.MkdirAll(a.WorkDir, 0o700); err != nil {
		return "", err
	}
	var b strings.Builder
	if len(proxies) > 0 {
		first := proxies[0]
		fmt.Fprintf(&b, "serverAddr = %q\n", first.ServerAddr)
		fmt.Fprintf(&b, "serverPort = %d\n", first.ServerPort)
	} else {
		b.WriteString("serverAddr = \"127.0.0.1\"\nserverPort = 7000\n")
	}
	// NOTE: usage metering is collected server-side from the frps admin API
	// (detail.md Milestone 3.3 + §1); frpc v0.71 exposes no client traffic API.
	// detail.md §9: identity travels in the frpc "user" field as
	// "<agentId>.<deviceToken>"; the plugin validates it on every op.
	fmt.Fprintf(&b, "user = %q\n\n", agentID+"."+deviceToken)

	for _, p := range proxies {
		fmt.Fprintf(&b, "[[proxies]]\n")
		fmt.Fprintf(&b, "name = %q\n", p.Name)
		fmt.Fprintf(&b, "type = %q\n", p.Type)
		fmt.Fprintf(&b, "localIP = %q\n", p.LocalHost)
		fmt.Fprintf(&b, "localPort = %d\n", p.LocalPort)
		fmt.Fprintf(&b, "remotePort = %d\n", p.RemotePort)
		if p.BandwidthLimitMbps > 0 {
			// frp's unit is bytes/s ("MB" = 1024*1024 bytes, pkg/config/types);
			// our field is megabits/s, so convert: Mbps * 1e6 / 8 = bytes/s,
			// expressed in KB (1024 bytes) for precision.
			kb := p.BandwidthLimitMbps * 125000 / 1024
			fmt.Fprintf(&b, "transport.bandwidthLimit = %q\n", fmt.Sprintf("%dKB", kb))
		}
		b.WriteString("\n")
	}

	path := filepath.Join(a.WorkDir, "frpc.toml")
	if err := os.WriteFile(path, []byte(b.String()), 0o600); err != nil {
		return "", err
	}
	return path, nil
}

// Command builds the exec.Cmd for frpc against a rendered config.
func (a *Adapter) Command(configPath string) *exec.Cmd {
	return exec.Command(a.FrpcPath, "-c", configPath)
}
