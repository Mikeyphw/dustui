package readiness

import "testing"

func TestCurrentIsOV09PackageStorageAuthority(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV09" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine {
		t.Fatalf("Android production buildability remains suspended until OV13: %+v", report)
	}
	if !report.MutationAuthority || !report.PackageStorageAuthority || !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV09 must expose package-storage/mutation/read/runtime/shell/root-boundary/cleanup contracts: %+v", report)
	}
	if len(report.RequiredTables) < 21 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV09 readiness lacks table/protocol coverage: %+v", report)
	}
}
