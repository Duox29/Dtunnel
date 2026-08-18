//go:build !linux

package nodeagent

// Non-Linux fallbacks: the node agent still reports basics (hostname, CPU
// count, frps proxy count) but OS-level metrics are Linux-only for now.

func loadAvg() []float64 { return nil }

func memInfo() (total, free uint64, ok bool) { return 0, 0, false }

func diskInfo(path string) (total, free uint64, ok bool) { return 0, 0, false }
