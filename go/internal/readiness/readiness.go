package readiness

import (
	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/state"
	"github.com/mikeyphw/dustui/go/internal/version"
)

// Report advertises supported runtime interfaces. It is intentionally NOT an
// installed-device health check or an APK/release qualification attestation.
type Report struct {
	Name                      string                  `json:"name"`
	Protocol                  string                  `json:"protocol"`
	SchemaProtocol            string                  `json:"schemaProtocol"`
	ProductProtocol           string                  `json:"productProtocol"`
	AndroidRuntimeProtocol    string                  `json:"androidRuntimeProtocol"`
	ProductionEngine          bool                    `json:"productionEngine"`
	SQLiteAuthorityModel      bool                    `json:"sqliteAuthorityModel"`
	ReadAuthority             bool                    `json:"readAuthority"`
	RuntimeInterfaceAvailable bool                    `json:"runtimeInterfaceAvailable"`
	ShellScaffold             bool                    `json:"shellScaffold"`
	PrivilegedBoundary        bool                    `json:"privilegedBoundary"`
	MutationAuthority         bool                    `json:"mutationAuthority"`
	PackageStorageAuthority   bool                    `json:"packageStorageAuthority"`
	CleanupIntelligence       bool                    `json:"cleanupIntelligence"`
	AutomationActivity        bool                    `json:"automationActivity"`
	DiagnosticsEvidence       bool                    `json:"diagnosticsEvidence"`
	PrivacyBoundedSupport     bool                    `json:"privacyBoundedSupport"`
	CleanupCapability         bool                    `json:"cleanupCapability"`
	GoCLIDefault              bool                    `json:"goCliDefault"`
	GoTUIDefault              bool                    `json:"goTuiDefault"`
	AndroidBuildability       string                  `json:"androidBuildability"`
	RequiredTables            []string                `json:"requiredTables"`
	EnvelopeTypes             []protocol.EnvelopeType `json:"envelopeTypes"`
	Message                   string                  `json:"message"`
	PersistenceAuthority      string                  `json:"persistenceAuthority"`
	PersistenceSchema         int                     `json:"persistenceSchema"`
}

func Current() Report {
	return Report{
		Name:                   version.Name,
		Protocol:               version.Protocol,
		SchemaProtocol:         version.SchemaProtocol,
		ProductProtocol:        product.Protocol,
		AndroidRuntimeProtocol: product.AndroidRuntimeProtocol,
		ProductionEngine:       true,
		SQLiteAuthorityModel:   true,
		ReadAuthority:          true,
		// The runtime interface exists in source; this does not establish
		// that any particular APK packages it or passed device checks.
		RuntimeInterfaceAvailable: true,
		ShellScaffold:             true,
		PrivilegedBoundary:        true,
		MutationAuthority:         true,
		PackageStorageAuthority:   true,
		CleanupIntelligence:       true,
		AutomationActivity:        true,
		DiagnosticsEvidence:       true,
		PrivacyBoundedSupport:     true,
		CleanupCapability:         true,
		GoCLIDefault:              true,
		GoTUIDefault:              true,
		AndroidBuildability:       product.AndroidBuildState,
		RequiredTables:            append([]string{}, state.RequiredTables...),
		EnvelopeTypes:             protocol.SupportedEnvelopeTypes(),
		Message:                   "Go/SQLite runtime interfaces are available. APK integrity, signing and device readiness require independent verification.",
		PersistenceAuthority:      "go-sqlite",
		PersistenceSchema:         state.SchemaVersion,
	}
}
