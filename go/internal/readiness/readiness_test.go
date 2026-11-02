package readiness

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/product"
)

func TestCurrentReadinessDoesNotClaimReleaseOrDeviceQualification(t *testing.T) {
	report := Current()
	if report.ProductProtocol != product.Protocol || report.AndroidRuntimeProtocol != product.AndroidRuntimeProtocol {
		t.Fatalf("runtime protocols diverged: %+v", report)
	}
	if !report.ProductionEngine || !report.GoCLIDefault || !report.GoTUIDefault || !report.SQLiteAuthorityModel {
		t.Fatalf("Go/SQLite authority must remain present: %+v", report)
	}
	if report.AndroidBuildability != "mandatory" {
		t.Fatalf("Android build requirement missing: %+v", report)
	}
	payload, err := json.Marshal(report)
	if err != nil {
		t.Fatal(err)
	}
	for _, obsolete := range []string{"migrationPhase", "androidReconnectGate", "gateDQualified", "gateEQualified", "legacyAndroidAuthority", "androidRuntimeBound"} {
		if strings.Contains(string(payload), obsolete) {
			t.Fatalf("stale readiness field %s: %s", obsolete, payload)
		}
	}
}
