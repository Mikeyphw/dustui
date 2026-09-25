package product

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/qualification"
)

func TestAUD_FIX07CurrentContractSeparatesHostDefaultFromAndroidGateD(t *testing.T) {
	contract := CurrentContract()
	if err := ValidateContract(contract); err != nil {
		t.Fatal(err)
	}
	if contract.AndroidBuildability != "mandatory" || !contract.AndroidBuildabilityMandatory {
		t.Fatalf("android buildability not mandatory: %+v", contract)
	}
	if contract.GateDQualified != qualification.GateDQualified {
		t.Fatalf("Gate D truth diverged from canonical qualification state: %+v", contract)
	}
	if !contract.ProductionEngine || !contract.GoSQLiteProductDefault || !contract.HostAuthorityDefault {
		t.Fatalf("Go host authority must be production-default at AUD-FIX-07: %+v", contract)
	}
	if contract.LegacyPythonTextualProduction || contract.PythonParityOraclePresent != qualification.PythonTextualOraclePresent {
		t.Fatalf("Python/Textual must be oracle-only, not production: %+v", contract)
	}
	if contract.LegacyDomainEnginesRequired != qualification.RoomArtifactsPresent {
		t.Fatalf("Android predecessor observation is not evidence-derived: %+v", contract)
	}
	if len(contract.RequiredEvidence) != len(qualification.RequiredEvidenceNodes) {
		t.Fatalf("required evidence topology diverged from canonical qualification state: %+v", contract.RequiredEvidence)
	}
}

func TestAUD_FIX07HostSurfacesDefaultWhileAndroidRemainsGateDControlled(t *testing.T) {
	contract := CurrentContract()
	required := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range contract.Surfaces {
		required[surface.Name] = true
		expected := true
		if surface.Name == SurfaceAndroid {
			expected = qualification.GateDQualified
		}
		if surface.ProductDefault != expected {
			t.Fatalf("surface %s default=%v want %v", surface.Name, surface.ProductDefault, expected)
		}
		if !surface.UsesSameEngineSemantics {
			t.Fatalf("surface %s lost shared Go semantics", surface.Name)
		}
	}
	for surface, ok := range required {
		if !ok {
			t.Fatalf("missing surface %s", surface)
		}
	}
}
