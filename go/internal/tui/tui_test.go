package tui

import (
	"testing"

	"github.com/mikeyphw/dustui/go/internal/shell"
)

func TestGoTUISmokePreservesRoutesAndResponsiveLayout(t *testing.T) {
	smoke := Smoke()
	if smoke.Phase != "OV13" || !smoke.SharedEngineSemantics {
		t.Fatalf("bad smoke: %+v", smoke)
	}
	if len(smoke.Routes) != 6 || len(smoke.InteractiveWorkflows) < 7 {
		t.Fatalf("incomplete smoke: %+v", smoke)
	}
	compact, err := Render(shell.RouteClean, 80, shell.StateBlocked)
	if err != nil {
		t.Fatal(err)
	}
	wide, err := Render(shell.RouteClean, 120, shell.StateBlocked)
	if err != nil {
		t.Fatal(err)
	}
	if compact.Route != wide.Route || compact.Layout != LayoutCompact || wide.Layout != LayoutWide {
		t.Fatalf("route identity must survive layout changes: compact=%+v wide=%+v", compact, wide)
	}
}
