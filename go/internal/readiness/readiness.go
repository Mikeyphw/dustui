package readiness

import (
	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/qualification"
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
	AutomationActivity      bool                    `json:"automationActivity"`
	DiagnosticsEvidence     bool                    `json:"diagnosticsEvidence"`
	PrivacyBoundedSupport   bool                    `json:"privacyBoundedSupport"`
	CleanupCapability       bool                    `json:"cleanupCapability"`
	GoCLIDefault            bool                    `json:"goCliDefault"`
	GoTUIDefault            bool                    `json:"goTuiDefault"`
	AndroidReconnectGate    string                  `json:"androidReconnectGate"`
	AndroidBuildability     string                  `json:"androidBuildability"`
	RequiredTables          []string                `json:"requiredTables"`
	EnvelopeTypes           []protocol.EnvelopeType `json:"envelopeTypes"`
	Message                 string                  `json:"message"`
	GateDQualified          bool                    `json:"gateDQualified"`
	AndroidRuntimeBound     bool                    `json:"androidRuntimeBound"`
	GoSQLiteImplemented     bool                    `json:"goSQLiteImplemented"`
	LegacyAndroidAuthority  bool                    `json:"legacyAndroidAuthority"`
	PersistenceAuthority    string                  `json:"persistenceAuthority"`
	PersistenceSchema       int                     `json:"persistenceSchema"`
}

func Current() Report {
	return Report{
		Name:                    version.Name,
		MigrationPhase:          version.MigrationPhase,
		Protocol:                version.Protocol,
		SchemaProtocol:          version.SchemaProtocol,
		ProductionEngine:        qualification.GateDQualified,
		SQLiteAuthorityModel:    true,
		ReadAuthority:           true,
		RuntimePackaged:         true,
		ShellScaffold:           true,
		PrivilegedBoundary:      true,
		MutationAuthority:       true,
		PackageStorageAuthority: true,
		CleanupIntelligence:     true,
		AutomationActivity:      true,
		DiagnosticsEvidence:     true,
		PrivacyBoundedSupport:   true,
		CleanupCapability:       true,
		GoCLIDefault:            qualification.GateDQualified,
		GoTUIDefault:            qualification.GateDQualified,
		AndroidReconnectGate:    product.Gate,
		AndroidBuildability:     product.AndroidBuildState,
		RequiredTables:          append([]string{}, state.RequiredTables...),
		EnvelopeTypes:           protocol.SupportedEnvelopeTypes(),
		Message:                 "Qualification truth is derived from migration/qualification/v1/gate_d_evidence.json; source capabilities do not close Gate D.",
		GateDQualified:          qualification.GateDQualified,
		AndroidRuntimeBound:     qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent,
		GoSQLiteImplemented:     qualification.GoSQLiteImplementationPresent,
		LegacyAndroidAuthority:  qualification.RoomArtifactsPresent || qualification.LegacyNavigationPresent,
		PersistenceAuthority:    "go-sqlite",
		PersistenceSchema:       state.SchemaVersion,
	}
}
