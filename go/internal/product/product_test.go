package product

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCurrentProductContract(t *testing.T) {
	c := CurrentContract()
	if err := ValidateContract(c); err != nil {
		t.Fatal(err)
	}
	if c.Protocol != "dustui.product.v2" || !c.ProductionEngine || !c.GoSQLiteProductDefault || !c.HostAuthorityDefault {
		t.Fatalf("invalid current product authority: %+v", c)
	}
	if c.AndroidBuildability != "mandatory" || !c.AndroidBuildabilityMandatory {
		t.Fatalf("Android hosted verification must remain mandatory: %+v", c)
	}
	payload, err := json.Marshal(c)
	if err != nil {
		t.Fatal(err)
	}
	for _, forbidden := range []string{"\"phase\"", "\"gate\"", "gateDQualified", "gateEQualified", "legacyDomainEnginesRequired", "candidateAcceptance", "qualificationState", "requiredEvidence", "oraclePresent"} {
		if strings.Contains(string(payload), forbidden) {
			t.Fatalf("obsolete qualification field %q in %s", forbidden, payload)
		}
	}
	for _, required := range []string{"\"androidRuntimeProtocol\"", "\"sqliteSchemaProtocol\"", "\"routes\"", "\"actionGates\""} {
		if !strings.Contains(string(payload), required) {
			t.Fatalf("missing live field %q", required)
		}
	}
}

func TestCurrentSurfaceAndRouteInvariants(t *testing.T) {
	c := CurrentContract()
	expected := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range c.Surfaces {
		if !surface.ProductDefault || !surface.UsesSameEngineSemantics {
			t.Fatalf("invalid surface %s", surface.Name)
		}
		expected[surface.Name] = true
	}
	for surface, seen := range expected {
		if !seen {
			t.Fatalf("missing surface %s", surface)
		}
	}
	c.AndroidTaskFlows[0].ActionAuthority = "blocked"
	if err := ValidateContract(c); err == nil {
		t.Fatal("a blocked semantic flow must be rejected")
	}
}
