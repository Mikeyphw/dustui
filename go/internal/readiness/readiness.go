package readiness

import (
	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/state"
	"github.com/mikeyphw/dustui/go/internal/version"
)

type Report struct {
	Name                    string                  `json:"name"`
	MigrationPhase          string                  `json:"migrationPhase"`
	Protocol                string                  `json:"protocol"`
	SchemaProtocol          string                  `json:"schemaProtocol"`
	ProductionEngine        bool                    `json:"productionEngine"`
	SQLiteAuthorityModel    bool                    `json:"sqliteAuthorityModel"`
	ReadAuthority           bool                    `json:"readAuthority"`
	RuntimePackaged         bool                    `json:"runtimePackaged"`
	ShellScaffold           bool                    `json:"shellScaffold"`
	PrivilegedBoundary      bool                    `json:"privilegedBoundary"`
	MutationAuthority       bool                    `json:"mutationAuthority"`
	PackageStorageAuthority bool                    `json:"packageStorageAuthority"`
	CleanupCapability       bool                    `json:"cleanupCapability"`
	RequiredTables          []string                `json:"requiredTables"`
	EnvelopeTypes           []protocol.EnvelopeType `json:"envelopeTypes"`
	Message                 string                  `json:"message"`
}

func Current() Report {
	return Report{
		Name:                    version.Name,
		MigrationPhase:          version.MigrationPhase,
		Protocol:                version.Protocol,
		SchemaProtocol:          version.SchemaProtocol,
		ProductionEngine:        false,
		SQLiteAuthorityModel:    true,
		ReadAuthority:           true,
		RuntimePackaged:         true,
		ShellScaffold:           true,
		PrivilegedBoundary:      true,
		MutationAuthority:       true,
		PackageStorageAuthority: true,
		CleanupCapability:       true,
		RequiredTables:          append([]string{}, state.RequiredTables...),
		EnvelopeTypes:           protocol.SupportedEnvelopeTypes(),
		Message:                 "OV09 Go package/storage evidence normalizes Android package-user facts; residue and cleanup eligibility require user-aware evidence plus action-time re-evaluation, never regex/path guesses alone.",
	}
}
