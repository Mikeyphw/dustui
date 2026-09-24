package readiness

import "testing"

func TestCurrentIsNonAuthoritativeOV01(t *testing.T) {
	report := Current()
	if report.MigrationPhase != "OV01" {
		t.Fatalf("phase = %q", report.MigrationPhase)
	}
	if report.ProductionEngine || report.SQLiteAuthority || report.MutationAuthority {
		t.Fatalf("OV01 readiness skeleton must not claim production authority: %+v", report)
	}
	if report.Protocol == "" || report.Message == "" {
		t.Fatalf("readiness report must expose protocol and message: %+v", report)
	}
}
