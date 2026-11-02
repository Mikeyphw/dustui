package engine

import "github.com/mikeyphw/dustui/go/internal/version"

// Kernel exposes the engine's role, not a migration phase or release status.
type Kernel struct {
	Authority  string
	Production bool
	Mutation   bool
}

func CurrentKernel() Kernel {
	// This compatibility kernel is a non-mutating entrypoint. The Go/SQLite
	// production host is exposed separately by the host and product packages.
	return Kernel{Authority: version.Authority, Production: false, Mutation: false}
}
