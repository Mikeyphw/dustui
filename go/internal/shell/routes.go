package shell

import (
	"errors"
	"fmt"
	"net/url"
	"strings"
)

type RouteID string

const (
	RouteOverview RouteID = "overview"
	RouteFiles    RouteID = "files"
	RouteClean    RouteID = "clean"
	RouteApps     RouteID = "apps"
	RouteActivity RouteID = "activity"
	RouteSystem   RouteID = "system"
)

type ContentState string

const (
	StateLoading ContentState = "loading"
	StateEmpty   ContentState = "empty"
	StateBlocked ContentState = "blocked"
	StateError   ContentState = "error"
	StateContent ContentState = "content"
)

type ActionGate string

const (
	GateAllowed         ActionGate = "allowed"
	GateBlocked         ActionGate = "blocked"
	GateNeedsFreshScan  ActionGate = "needs_fresh_scan"
	GateNeedsCapability ActionGate = "needs_capability"
)

type Route struct {
	ID         RouteID `json:"id"`
	EntityID   string  `json:"entityId,omitempty"`
	Generation int64   `json:"generation"`
}

type BoundResponse struct {
	RouteID    RouteID      `json:"routeId"`
	EntityID   string       `json:"entityId,omitempty"`
	Generation int64        `json:"generation"`
	State      ContentState `json:"state"`
}

type ShellContract struct {
	Routes               []RouteID      `json:"routes"`
	States               []ContentState `json:"states"`
	Gates                []ActionGate   `json:"gates"`
	ProvisionalGrouping  bool           `json:"provisionalGrouping"`
	AndroidReconnectGate string         `json:"androidReconnectGate"`
}

func CurrentContract() ShellContract {
	return ShellContract{
		Routes:               []RouteID{RouteOverview, RouteFiles, RouteClean, RouteApps, RouteActivity, RouteSystem},
		States:               []ContentState{StateLoading, StateEmpty, StateBlocked, StateError, StateContent},
		Gates:                []ActionGate{GateAllowed, GateBlocked, GateNeedsFreshScan, GateNeedsCapability},
		ProvisionalGrouping:  true,
		AndroidReconnectGate: "OV13",
	}
}

func TranslateDeepLink(raw string, currentGeneration int64) (Route, error) {
	if currentGeneration <= 0 {
		return Route{}, errors.New("generation_required")
	}
	u, err := url.Parse(raw)
	if err != nil {
		return Route{}, err
	}
	id := RouteID(strings.Trim(strings.TrimPrefix(u.Path, "/"), "/"))
	if id == "" && u.Host != "" {
		id = RouteID(u.Host)
	}
	if id == "" {
		id = RouteOverview
	}
	if !validRoute(id) {
		return Route{}, fmt.Errorf("unknown_route:%s", id)
	}
	return Route{ID: id, EntityID: u.Query().Get("entity"), Generation: currentGeneration}, nil
}

func Restore(id RouteID, entity string, generation int64) (Route, error) {
	if !validRoute(id) {
		return Route{}, fmt.Errorf("unknown_route:%s", id)
	}
	if generation <= 0 {
		return Route{}, errors.New("generation_required")
	}
	return Route{ID: id, EntityID: entity, Generation: generation}, nil
}

func AcceptResponse(route Route, response BoundResponse) error {
	if route.ID != response.RouteID || route.EntityID != response.EntityID || route.Generation != response.Generation {
		return errors.New("stale_or_cross_route_response")
	}
	if !validState(response.State) {
		return fmt.Errorf("unknown_state:%s", response.State)
	}
	return nil
}

func validRoute(id RouteID) bool {
	for _, route := range CurrentContract().Routes {
		if route == id {
			return true
		}
	}
	return false
}

func validState(state ContentState) bool {
	for _, item := range CurrentContract().States {
		if item == state {
			return true
		}
	}
	return false
}
