package tui

import (
	"fmt"

	"github.com/mikeyphw/dustui/go/internal/product"
	"github.com/mikeyphw/dustui/go/internal/shell"
)

type LayoutMode string

const (
	LayoutCompact LayoutMode = "compact"
	LayoutWide    LayoutMode = "wide"
)

type Screen struct {
	Route      shell.RouteID      `json:"route"`
	Layout     LayoutMode         `json:"layout"`
	State      shell.ContentState `json:"state"`
	Actions    []shell.ActionGate `json:"actions"`
	StatusLine string             `json:"statusLine"`
}

type SmokeResult struct {
	Phase                 string             `json:"phase"`
	Surface               product.Surface    `json:"surface"`
	Layouts               []LayoutMode       `json:"layouts"`
	Routes                []shell.RouteID    `json:"routes"`
	SharedEngineSemantics bool               `json:"sharedEngineSemantics"`
	InteractiveWorkflows  []product.Workflow `json:"interactiveWorkflows"`
}

func LayoutFor(width int) LayoutMode {
	if width >= 95 {
		return LayoutWide
	}
	return LayoutCompact
}

func Render(route shell.RouteID, width int, state shell.ContentState) (Screen, error) {
	if _, err := shell.Restore(route, "", 1); err != nil {
		return Screen{}, err
	}
	if state == "" {
		state = shell.StateContent
	}
	if state != shell.StateLoading && state != shell.StateEmpty && state != shell.StateBlocked && state != shell.StateError && state != shell.StateContent {
		return Screen{}, fmt.Errorf("unknown_state:%s", state)
	}
	return Screen{Route: route, Layout: LayoutFor(width), State: state, Actions: []shell.ActionGate{shell.GateAllowed, shell.GateNeedsFreshScan, shell.GateNeedsCapability}, StatusLine: "Go TUI uses DustUI Go engine semantics"}, nil
}

func Smoke() SmokeResult {
	contract := product.CurrentContract()
	routes := append([]shell.RouteID{}, contract.Routes...)
	return SmokeResult{Phase: product.Phase, Surface: product.SurfaceTUI, Layouts: []LayoutMode{LayoutCompact, LayoutWide}, Routes: routes, SharedEngineSemantics: true, InteractiveWorkflows: contract.Surfaces[1].SupportedWorkflows}
}
