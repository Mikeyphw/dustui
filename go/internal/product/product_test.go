package product

import "testing"

func TestCurrentContractCutsOverAllProductSurfaces(t *testing.T) {
	contract := CurrentContract()
	if err := ValidateContract(contract); err != nil {
		t.Fatal(err)
	}
	if contract.AndroidBuildability != "mandatory" || !contract.AndroidBuildabilityMandatory {
		t.Fatalf("android buildability not mandatory: %+v", contract)
	}
	if contract.LegacyDomainEnginesRequired || contract.LegacyPythonTextualProduction {
		t.Fatalf("legacy engines must not be required after Gate D: %+v", contract)
	}
	required := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range contract.Surfaces {
		required[surface.Name] = true
		if !surface.UsesSameEngineSemantics {
			t.Fatalf("surface %s must use shared Go semantics", surface.Name)
		}
	}
	for surface, ok := range required {
		if !ok {
			t.Fatalf("missing surface %s", surface)
		}
	}
}
