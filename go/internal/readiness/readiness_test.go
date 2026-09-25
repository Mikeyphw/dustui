package readiness

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/qualification"
)

func TestCurrentIsEvidenceDerivedGateDReadiness(t *testing.T) {
	report := Current()
	if report.AndroidBuildability != "mandatory" || report.AndroidReconnectGate != "Gate D" {
		t.Fatalf("unexpected reconnect contract: %+v", report)
	}
	if report.GateDQualified != qualification.GateDQualified || report.ProductionEngine != qualification.GateDQualified {
		t.Fatalf("readiness must consume canonical qualification state: %+v", report)
	}
	if report.GoCLIDefault != qualification.GateDQualified || report.GoTUIDefault != qualification.GateDQualified {
		t.Fatalf("CLI/TUI default status must not be aspirational: %+v", report)
	}
	if report.AndroidRuntimeBound != (qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent) {
		t.Fatalf("runtime binding observation is stale: %+v", report)
	}
	if report.GoSQLiteImplemented != qualification.GoSQLiteImplementationPresent {
		t.Fatalf("SQLite observation is stale: %+v", report)
	}
}
