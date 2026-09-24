package engine

import "github.com/mikeyphw/dustui/go/internal/version"

type Kernel struct {
	Phase      string
	Production bool
	Mutation   bool
}

func CurrentKernel() Kernel {
	return Kernel{Phase: version.MigrationPhase, Production: false, Mutation: false}
}
