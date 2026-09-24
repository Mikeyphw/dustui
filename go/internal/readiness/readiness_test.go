package readiness

import "testing"

func TestCurrentIsOV02StateProtocolKernelWithoutProductionCutover(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV02" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine || report.MutationAuthority {
		t.Fatalf("OV02 must not claim production/mutation authority: %+v", report)
	}
	if !report.SQLiteAuthorityModel {
		t.Fatalf("OV02 must expose Go SQLite authority model: %+v", report)
	}
	if len(report.RequiredTables) < 13 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV02 readiness lacks table/protocol coverage: %+v", report)
	}
}
