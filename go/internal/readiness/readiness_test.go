package readiness

import "testing"

func TestCurrentIsOV12DiagnosticsEvidenceAuthority(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV12" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine {
		t.Fatalf("Android production buildability remains suspended until OV13: %+v", report)
	}
	if !report.MutationAuthority || !report.PackageStorageAuthority || !report.CleanupIntelligence || !report.AutomationActivity || !report.DiagnosticsEvidence || !report.PrivacyBoundedSupport || !report.SQLiteAuthorityModel || !report.ReadAuthority || !report.RuntimePackaged || !report.ShellScaffold || !report.PrivilegedBoundary || !report.CleanupCapability {
		t.Fatalf("OV12 must expose diagnostics/evidence/privacy plus automation/activity, cleanup-intelligence, package-storage, mutation, read, runtime, shell, root-boundary, and cleanup contracts: %+v", report)
	}
	if len(report.RequiredTables) < 45 || len(report.EnvelopeTypes) < 9 {
		t.Fatalf("OV12 readiness lacks table/protocol coverage: %+v", report)
	}
}
