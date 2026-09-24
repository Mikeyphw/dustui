package readiness

import "testing"

func TestCurrentIsOV06PrivilegedBoundaryWithoutMutationCutover(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV06" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine || report.MutationAuthority {
		t.Fatalf("OV06 must not claim Android production/mutation authority: %+v", report)
	}
	if !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary {
		t.Fatalf("OV06 must expose SQLite/read/runtime/shell/root-boundary contracts: %+v", report)
	}
	if len(report.RequiredTables) < 18 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV05 readiness lacks table/protocol coverage: %+v", report)
	}
}
