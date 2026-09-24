package readiness

import "testing"

func TestCurrentIsOV10CleanupIntelligenceAuthority(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV10" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine {
		t.Fatalf("Android production buildability remains suspended until OV13: %+v", report)
	}
	if !report.MutationAuthority || !report.PackageStorageAuthority || !report.CleanupIntelligence || !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV10 must expose cleanup-intelligence/package-storage/mutation/read/runtime/shell/root-boundary/cleanup contracts: %+v", report)
	}
	if len(report.RequiredTables) < 35 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV10 readiness lacks table/protocol coverage: %+v", report)
	}
}
