package readiness

import "testing"

func TestCurrentIsOV07CleanupCapabilityWithoutMutationCutover(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV07" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine || report.MutationAuthority {
		t.Fatalf("OV07 must not claim Android production/mutation authority: %+v", report)
	}
	if !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV07 must expose SQLite/read/runtime/shell/root-boundary/cleanup-capability contracts: %+v", report)
	}
	if len(report.RequiredTables) < 18 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV07 readiness lacks table/protocol coverage: %+v", report)
	}
}
