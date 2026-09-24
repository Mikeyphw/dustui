package readiness

import (
	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/state"
	"github.com/mikeyphw/dustui/go/internal/version"
)

type Report struct {
	Name                 string                  `json:"name"`
	MigrationPhase       string                  `json:"migrationPhase"`
	Protocol             string                  `json:"protocol"`
	SchemaProtocol       string                  `json:"schemaProtocol"`
	ProductionEngine     bool                    `json:"productionEngine"`
	SQLiteAuthorityModel bool                    `json:"sqliteAuthorityModel"`
	ReadAuthority        bool                    `json:"readAuthority"`
	RuntimePackaged      bool                    `json:"runtimePackaged"`
	ShellScaffold        bool                    `json:"shellScaffold"`
	MutationAuthority    bool                    `json:"mutationAuthority"`
	RequiredTables       []string                `json:"requiredTables"`
	EnvelopeTypes        []protocol.EnvelopeType `json:"envelopeTypes"`
	Message              string                  `json:"message"`
}

func Current() Report {
	return Report{
		Name:                 version.Name,
		MigrationPhase:       version.MigrationPhase,
		Protocol:             version.Protocol,
		SchemaProtocol:       version.SchemaProtocol,
		ProductionEngine:     false,
		SQLiteAuthorityModel: true,
		ReadAuthority:        true,
		RuntimePackaged:      true,
		ShellScaffold:        true,
		MutationAuthority:    false,
		RequiredTables:       append([]string{}, state.RequiredTables...),
		EnvelopeTypes:        protocol.SupportedEnvelopeTypes(),
		Message:              "OV05 Go runtime/read-authority contract is present; Android buildability remains intentionally suspended until OV13 reconnect.",
	}
}
