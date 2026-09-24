package readiness

import "testing"

func TestCurrentIsOV05RuntimeReadAuthorityWithoutMutationCutover(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV05" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine || report.MutationAuthority {
		t.Fatalf("OV05 must not claim Android production/mutation authority: %+v", report)
	}
	if !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold {
		t.Fatalf("OV05 must expose SQLite/read/runtime/shell contracts: %+v", report)
	}
	if len(report.RequiredTables) < 15 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV05 readiness lacks table/protocol coverage: %+v", report)
	}
}
