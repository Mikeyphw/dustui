package readiness

import "testing"

func TestCurrentIsOV13ProductDefault(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV13" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if !report.ProductionEngine || report.AndroidBuildability != "mandatory" || report.AndroidReconnectGate != "Gate D" {
		t.Fatalf("Gate D readiness missing: %+v", report)
	}
	if !report.GoCLIDefault || !report.GoTUIDefault {
		t.Fatalf("Go CLI/TUI must be product defaults: %+v", report)
	}
	if report.GateDQualified || !report.AndroidRuntimeBound || report.GoSQLiteImplemented || !report.LegacyAndroidAuthority {
		t.Fatalf("OV14 truth audit qualification state is wrong: %+v", report)
	}
	if !report.MutationAuthority || !report.PackageStorageAuthority || !report.CleanupIntelligence || !report.AutomationActivity || !report.DiagnosticsEvidence || !report.PrivacyBoundedSupport || !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV13 must retain all prior authorities: %+v", report)
	}
	if len(report.RequiredTables) < 48 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV13 readiness lacks table/protocol coverage: %+v", report)
	}
}
