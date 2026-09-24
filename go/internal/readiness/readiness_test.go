package readiness

import "testing"

func TestCurrentIsOV11AutomationActivityAuthority(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV11" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine {
		t.Fatalf("Android production buildability remains suspended until OV13: %+v", report)
	}
	if !report.MutationAuthority || !report.PackageStorageAuthority || !report.CleanupIntelligence || !report.AutomationActivity || !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV11 must expose automation/activity plus cleanup-intelligence/package-storage/mutation/read/runtime/shell/root-boundary/cleanup contracts: %+v", report)
	}
	if len(report.RequiredTables) < 39 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV11 readiness lacks table/protocol coverage: %+v", report)
	}
}
