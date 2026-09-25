package product

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/qualification"
)

func TestCurrentContractUsesCanonicalQualificationTruth(t *testing.T) {
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
	if contract.ProductionEngine != qualification.GateDQualified || contract.GoSQLiteProductDefault != qualification.GateDQualified {
		t.Fatalf("product default must not be declared before canonical qualification: %+v", contract)
	}
	if contract.LegacyPythonTextualProduction != qualification.LegacyPythonTextualPresent || contract.LegacyDomainEnginesRequired != qualification.RoomArtifactsPresent {
		t.Fatalf("legacy observations are not evidence-derived: %+v", contract)
	}
	if len(contract.RequiredEvidence) != len(qualification.RequiredEvidenceNodes) {
		t.Fatalf("required evidence topology diverged from canonical qualification state: %+v", contract.RequiredEvidence)
	}
}

func TestCurrentContractStillDescribesAllTargetSurfaces(t *testing.T) {
	contract := CurrentContract()
	required := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range contract.Surfaces {
		required[surface.Name] = true
		if surface.ProductDefault != qualification.GateDQualified {
			t.Fatalf("surface %s default state does not match canonical qualification", surface.Name)
		}
	}
	for surface, ok := range required {
		if !ok {
			t.Fatalf("missing surface %s", surface)
		}
	}
}
