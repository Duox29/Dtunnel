// Package identity implements detail.md §6: on first run the agent generates
// an Ed25519 keypair; the public key is the durable device identity bound to
// a user account. The key survives agent reinstalls but not reformatting.
package identity

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
)

type KeyPair struct {
	Public  ed25519.PublicKey
	Private ed25519.PrivateKey
}

type stored struct {
	PublicKey  string `json:"public_key"`
	PrivateKey string `json:"private_key"`
}

// PublicKeyB64 returns the durable device identity as base64.
func (k *KeyPair) PublicKeyB64() string {
	return base64.StdEncoding.EncodeToString(k.Public)
}

// Dir returns the OS-appropriate secure storage location (§6).
func Dir() (string, error) {
	switch runtime.GOOS {
	case "windows":
		appdata := os.Getenv("APPDATA")
		if appdata == "" {
			return "", fmt.Errorf("APPDATA not set")
		}
		return filepath.Join(appdata, "duox-agent"), nil
	default:
		home, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		return filepath.Join(home, ".config", "duox-agent"), nil
	}
}

// LoadOrGenerate reads the persisted keypair, creating one on first run.
func LoadOrGenerate() (*KeyPair, error) {
	dir, err := Dir()
	if err != nil {
		return nil, err
	}
	path := filepath.Join(dir, "identity.json")

	if data, err := os.ReadFile(path); err == nil {
		var s stored
		if err := json.Unmarshal(data, &s); err != nil {
			return nil, fmt.Errorf("corrupt identity file: %w", err)
		}
		priv, err := base64.StdEncoding.DecodeString(s.PrivateKey)
		if err != nil || len(priv) != ed25519.PrivateKeySize {
			return nil, fmt.Errorf("corrupt identity file")
		}
		return &KeyPair{
			Public:  ed25519.PrivateKey(priv).Public().(ed25519.PublicKey),
			Private: ed25519.PrivateKey(priv),
		}, nil
	}

	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	s := stored{
		PublicKey:  base64.StdEncoding.EncodeToString(pub),
		PrivateKey: base64.StdEncoding.EncodeToString(priv),
	}
	data, _ := json.MarshalIndent(s, "", "  ")
	if err := os.WriteFile(path, data, 0o600); err != nil {
		return nil, err
	}
	return &KeyPair{Public: pub, Private: priv}, nil
}
