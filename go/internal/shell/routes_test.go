package shell

import (
	"errors"
	"testing"
)

func TestAUD_FIX08SemanticRouteInventoryAndDeepLinks(t *testing.T) {
	contract := CurrentContract()
	if len(contract.Routes) != 27 || len(contract.PrimaryRoutes) != 6 || contract.ProvisionalGrouping || contract.AndroidReconnectGate != "Gate D" {
		t.Fatalf("bad semantic route contract: %+v", contract)
	}
	route, err := TranslateDeepLink("dustui://files/item/%2Fsdcard%2FDownload%2Fmovie.mkv", 44)
	if err != nil {
		t.Fatal(err)
	}
	if route.ID != RouteFilesItem || route.Domain != DomainFiles || route.EntityKind != "path" || route.EntityID != "/sdcard/Download/movie.mkv" || route.Generation != 44 {
		t.Fatalf("bad entity route: %+v", route)
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteFilesItem, EntityID: route.EntityID, Generation: 44, State: StateContent}); err != nil {
		t.Fatalf("fresh response rejected: %v", err)
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteFilesItem, EntityID: route.EntityID, Generation: 43, State: StateContent}); err == nil {
		t.Fatalf("stale response must be rejected")
	}
	if err := AcceptResponse(route, BoundResponse{RouteID: RouteFilesExplorer, EntityID: route.EntityID, Generation: 44, State: StateContent}); err == nil {
		t.Fatalf("cross-route response must be rejected")
	}
}

func TestAUD_FIX08DeepLinkRejectsMissingEntityAndUnknownRoute(t *testing.T) {
	_, err := TranslateDeepLink("dustui://activity/run", 7)
	var routeErr *RouteError
	if !errors.As(err, &routeErr) || routeErr.Code != "entity_required" || routeErr.EntityKind != "operation" {
		t.Fatalf("expected typed entity_required, got %#v", err)
	}
	_, err = TranslateDeepLink("dustui://tools/debug", 7)
	if !errors.As(err, &routeErr) || routeErr.Code != "unknown_route" {
		t.Fatalf("expected unknown_route, got %#v", err)
	}
}

func TestAUD_FIX08PrimaryRouteMeaningNeverDependsOnLayout(t *testing.T) {
	want := []RouteID{RouteOverviewHome, RouteFilesExplorer, RouteCleanRecommended, RouteAppsList, RouteActivityList, RouteSystemRuntime}
	got := PrimaryRoutes()
	if len(got) != len(want) {
		t.Fatalf("primary routes length = %d", len(got))
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("primary route %d = %q want %q", i, got[i], want[i])
		}
	}
}
