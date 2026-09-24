package shell

import "testing"

func TestShellRoutesAreStableGenerationBoundAndProvisional(t *testing.T) {
	contract := CurrentContract()
	if len(contract.Routes) != 6 || !contract.ProvisionalGrouping || contract.AndroidReconnectGate != "OV13" {
		t.Fatalf("bad shell contract: %+v", contract)
	}
	route, err := TranslateDeepLink("dustui://files?entity=/sdcard/Download", 44)
	if err != nil {
		t.Fatal(err)
	}
	if route.ID != RouteFiles || route.EntityID != "/sdcard/Download" || route.Generation != 44 {
		t.Fatalf("bad route: %+v", route)
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteFiles, EntityID: "/sdcard/Download", Generation: 44, State: StateContent}); err != nil {
		t.Fatalf("fresh response rejected: %v", err)
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteFiles, EntityID: "/sdcard/Download", Generation: 43, State: StateContent}); err == nil {
		t.Fatalf("stale response must be rejected")
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteOverview, EntityID: "/sdcard/Download", Generation: 44, State: StateContent}); err == nil {
		t.Fatalf("cross-route response must be rejected")
	}
}
