package runtime

import "github.com/duox/dtunnel-agent/identity"

// identityLoad wraps identity.LoadOrGenerate so runtime stays testable.
func identityLoad() (*identity.KeyPair, error) {
	return identity.LoadOrGenerate()
}
