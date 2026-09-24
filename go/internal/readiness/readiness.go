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
	CleanupIntelligence     bool                    `json:"cleanupIntelligence"`
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
		CleanupIntelligence:     true,
		CleanupCapability:       true,
		RequiredTables:          append([]string{}, state.RequiredTables...),
		EnvelopeTypes:           protocol.SupportedEnvelopeTypes(),
		Message:                 "OV10 Go cleanup intelligence owns duplicates, residue, recommendations, and rules; every result is proof-bearing and no specialized label may degrade to a bare path while still appearing verified or safe.",
	}
}
