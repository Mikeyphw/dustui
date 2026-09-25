package readiness

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/qualification"
)

func TestAUD_FIX07CurrentSeparatesGoHostReadinessFromAndroidReconnectGate(t *testing.T) {
	report := Current()
	if report.AndroidBuildability != "mandatory" || report.AndroidReconnectGate != "Gate D" {
		t.Fatalf("unexpected reconnect contract: %+v", report)
	}
	if report.GateDQualified != qualification.GateDQualified {
		t.Fatalf("Gate D must consume canonical qualification state: %+v", report)
	}
	if !report.ProductionEngine || !report.GoCLIDefault || !report.GoTUIDefault {
		t.Fatalf("Go CLI/TUI host cutover is not active: %+v", report)
	}
	if report.AndroidRuntimeBound != (qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent) {
		t.Fatalf("runtime binding observation is stale: %+v", report)
	}
	if report.GoSQLiteImplemented != qualification.GoSQLiteImplementationPresent {
		t.Fatalf("SQLite observation is stale: %+v", report)
	}
}
