package shell

import (
	"errors"
	"fmt"
	"net/url"
	"strings"
)

type RouteID string
type RouteDomain string

const (
	DomainOverview RouteDomain = "overview"
	DomainFiles    RouteDomain = "files"
	DomainClean    RouteDomain = "clean"
	DomainApps     RouteDomain = "apps"
	DomainActivity RouteDomain = "activity"
	DomainSystem   RouteDomain = "system"

	RouteOverviewHome        RouteID = "overview/home"
	RouteOverviewComposition RouteID = "overview/composition"
	RouteOverviewTrends      RouteID = "overview/trends"
	RouteOverviewScanHistory RouteID = "overview/scan-history"

	RouteFilesExplorer RouteID = "files/explorer"
	RouteFilesSearch   RouteID = "files/search"
	RouteFilesLarge    RouteID = "files/large"
	RouteFilesTreemap  RouteID = "files/treemap"
	RouteFilesItem     RouteID = "files/item"

	RouteCleanRecommended RouteID = "clean/recommended"
	RouteCleanDuplicates  RouteID = "clean/duplicates"
	RouteCleanResidue     RouteID = "clean/residue"
	RouteCleanRules       RouteID = "clean/rules"
	RouteCleanCandidate   RouteID = "clean/candidate"
	RouteCleanReview      RouteID = "clean/review"

	RouteAppsList    RouteID = "apps/list"
	RouteAppsPackage RouteID = "apps/package"
	RouteAppsPaths   RouteID = "apps/paths"

	RouteActivityList     RouteID = "activity/list"
	RouteActivityRun      RouteID = "activity/run"
	RouteActivityEvidence RouteID = "activity/evidence"

	RouteSystemRuntime       RouteID = "system/runtime"
	RouteSystemModules       RouteID = "system/modules"
	RouteSystemAutomation    RouteID = "system/automation"
	RouteSystemDiagnostics   RouteID = "system/diagnostics"
	RouteSystemCertification RouteID = "system/certification"
	RouteSystemSettings      RouteID = "system/settings"
)

// Compatibility aliases are intentionally top-level semantic defaults rather than
// predecessor destinations. They keep the Go TUI/host API source-compatible while
// AUD-FIX-08 moves the actual route identity to the nested route tree.
const (
	RouteOverview = RouteOverviewHome
	RouteFiles    = RouteFilesExplorer
	RouteClean    = RouteCleanRecommended
	RouteApps     = RouteAppsList
	RouteActivity = RouteActivityList
	RouteSystem   = RouteSystemRuntime
)

type RouteSpec struct {
	ID         RouteID     `json:"id"`
	Domain     RouteDomain `json:"domain"`
	EntityKind string      `json:"entityKind,omitempty"`
}

var routeSpecs = []RouteSpec{
	{RouteOverviewHome, DomainOverview, ""},
	{RouteOverviewComposition, DomainOverview, ""},
	{RouteOverviewTrends, DomainOverview, ""},
	{RouteOverviewScanHistory, DomainOverview, ""},
	{RouteFilesExplorer, DomainFiles, ""},
	{RouteFilesSearch, DomainFiles, ""},
	{RouteFilesLarge, DomainFiles, ""},
	{RouteFilesTreemap, DomainFiles, ""},
	{RouteFilesItem, DomainFiles, "path"},
	{RouteCleanRecommended, DomainClean, ""},
	{RouteCleanDuplicates, DomainClean, ""},
	{RouteCleanResidue, DomainClean, ""},
	{RouteCleanRules, DomainClean, ""},
	{RouteCleanCandidate, DomainClean, "candidate"},
	{RouteCleanReview, DomainClean, "manifest"},
	{RouteAppsList, DomainApps, ""},
	{RouteAppsPackage, DomainApps, "package"},
	{RouteAppsPaths, DomainApps, "package"},
	{RouteActivityList, DomainActivity, ""},
	{RouteActivityRun, DomainActivity, "operation"},
	{RouteActivityEvidence, DomainActivity, "operation"},
	{RouteSystemRuntime, DomainSystem, ""},
	{RouteSystemModules, DomainSystem, ""},
	{RouteSystemAutomation, DomainSystem, ""},
	{RouteSystemDiagnostics, DomainSystem, ""},
	{RouteSystemCertification, DomainSystem, ""},
	{RouteSystemSettings, DomainSystem, ""},
}

func PrimaryRoutes() []RouteID {
	return []RouteID{RouteOverviewHome, RouteFilesExplorer, RouteCleanRecommended, RouteAppsList, RouteActivityList, RouteSystemRuntime}
}

func SemanticRoutes() []RouteID {
	routes := make([]RouteID, 0, len(routeSpecs))
	for _, spec := range routeSpecs {
		routes = append(routes, spec.ID)
	}
	return routes
}

type ContentState string

const (
	StateLoading ContentState = "loading"
	StateEmpty   ContentState = "empty"
	StateBlocked ContentState = "blocked"
	StateError   ContentState = "error"
	StateContent ContentState = "content"
)

type Route struct {
	ID         RouteID     `json:"id"`
	Domain     RouteDomain `json:"domain"`
	EntityKind string      `json:"entityKind,omitempty"`
	EntityID   string      `json:"entityId,omitempty"`
	Generation int64       `json:"generation"`
}

type BoundResponse struct {
	RouteID     RouteID      `json:"routeId"`
	EntityKind  string       `json:"entityKind,omitempty"`
	EntityID    string       `json:"entityId,omitempty"`
	Generation  int64        `json:"generation"`
	RequestID   string       `json:"requestId,omitempty"`
	Fingerprint string       `json:"fingerprint,omitempty"`
	State       ContentState `json:"state"`
}

type ShellContract struct {
	Routes               []RouteID      `json:"routes"`
	PrimaryRoutes        []RouteID      `json:"primaryRoutes"`
	States               []ContentState `json:"states"`
	Gates                []ActionGate   `json:"gates"`
	ProvisionalGrouping  bool           `json:"provisionalGrouping"`
	AndroidReconnectGate string         `json:"androidReconnectGate"`
}

func CurrentContract() ShellContract {
	return ShellContract{
		Routes:        SemanticRoutes(),
		PrimaryRoutes: PrimaryRoutes(),
		States:        []ContentState{StateLoading, StateEmpty, StateBlocked, StateError, StateContent},
		Gates: []ActionGate{
			allowedGate(),
			blockedGate("scan_required", "A trusted scan is required before this action can run", RemediationAction{Code: "start_scan", Label: "Start scan", RouteKey: RouteOverviewHome}),
			blockedGate("scan_stale", "The scan is stale and must be refreshed before this action", RemediationAction{Code: "start_scan", Label: "Run a new scan", RouteKey: RouteOverviewHome}),
			blockedGate("capability_required", "The required capability is not currently available", RemediationAction{Code: "open_diagnostics", Label: "Open Diagnostics", RouteKey: RouteSystemDiagnostics}),
		},
		ProvisionalGrouping:  false,
		AndroidReconnectGate: "Gate D",
	}
}

type RouteError struct {
	Code       string  `json:"code"`
	RouteID    RouteID `json:"routeId,omitempty"`
	EntityKind string  `json:"entityKind,omitempty"`
	EntityID   string  `json:"entityId,omitempty"`
	Detail     string  `json:"detail"`
}

func (e *RouteError) Error() string {
	if e.Detail != "" {
		return e.Code + ":" + e.Detail
	}
	return e.Code
}

func TranslateDeepLink(raw string, currentGeneration int64) (Route, error) {
	if currentGeneration <= 0 {
		return Route{}, &RouteError{Code: "generation_required"}
	}
	u, err := url.Parse(raw)
	if err != nil {
		return Route{}, &RouteError{Code: "invalid_deep_link", Detail: err.Error()}
	}
	if !strings.EqualFold(u.Scheme, "dustui") {
		return Route{}, &RouteError{Code: "invalid_deep_link", Detail: "unsupported_scheme"}
	}
	segments := make([]string, 0, 6)
	if u.Host != "" {
		segments = append(segments, u.Host)
	}
	for _, part := range strings.Split(strings.Trim(u.EscapedPath(), "/"), "/") {
		if strings.TrimSpace(part) == "" {
			continue
		}
		decoded, decodeErr := url.PathUnescape(part)
		if decodeErr != nil {
			return Route{}, &RouteError{Code: "invalid_deep_link", Detail: "bad_path_encoding"}
		}
		segments = append(segments, decoded)
	}
	if len(segments) == 0 {
		return Restore(RouteOverviewHome, "", currentGeneration)
	}

	joined := strings.Join(segments, "/")
	var matched *RouteSpec
	for i := range routeSpecs {
		spec := &routeSpecs[i]
		key := string(spec.ID)
		if joined == key || strings.HasPrefix(joined, key+"/") {
			if matched == nil || len(key) > len(string(matched.ID)) {
				matched = spec
			}
		}
	}
	if matched == nil {
		return Route{}, &RouteError{Code: "unknown_route", Detail: joined}
	}
	entity := ""
	keyParts := strings.Split(string(matched.ID), "/")
	if len(segments) > len(keyParts) {
		entity = strings.Join(segments[len(keyParts):], "/")
	}
	return restoreSpec(*matched, entity, currentGeneration)
}

func Restore(id RouteID, entity string, generation int64) (Route, error) {
	if generation <= 0 {
		return Route{}, &RouteError{Code: "generation_required", RouteID: id}
	}
	spec, ok := specFor(id)
	if !ok {
		return Route{}, &RouteError{Code: "unknown_route", RouteID: id, Detail: string(id)}
	}
	return restoreSpec(spec, entity, generation)
}

func restoreSpec(spec RouteSpec, entity string, generation int64) (Route, error) {
	entity = strings.TrimSpace(entity)
	if spec.EntityKind != "" && entity == "" {
		return Route{}, &RouteError{Code: "entity_required", RouteID: spec.ID, EntityKind: spec.EntityKind}
	}
	if spec.EntityKind == "" && entity != "" {
		return Route{}, &RouteError{Code: "entity_not_allowed", RouteID: spec.ID, EntityID: entity}
	}
	return Route{ID: spec.ID, Domain: spec.Domain, EntityKind: spec.EntityKind, EntityID: entity, Generation: generation}, nil
}

func AcceptResponse(route Route, response BoundResponse) error {
	if route.ID != response.RouteID || route.EntityID != response.EntityID || route.Generation != response.Generation {
		return errors.New("stale_or_cross_route_response")
	}
	if response.EntityKind != "" && route.EntityKind != response.EntityKind {
		return errors.New("stale_or_cross_route_response")
	}
	if !validState(response.State) {
		return fmt.Errorf("unknown_state:%s", response.State)
	}
	return nil
}

func DomainFor(id RouteID) (RouteDomain, bool) {
	spec, ok := specFor(id)
	return spec.Domain, ok
}

func EntityKindFor(id RouteID) (string, bool) {
	spec, ok := specFor(id)
	return spec.EntityKind, ok
}

func specFor(id RouteID) (RouteSpec, bool) {
	for _, spec := range routeSpecs {
		if spec.ID == id {
			return spec, true
		}
	}
	return RouteSpec{}, false
}

func validRoute(id RouteID) bool {
	_, ok := specFor(id)
	return ok
}

func validState(state ContentState) bool {
	for _, item := range CurrentContract().States {
		if item == state {
			return true
		}
	}
	return false
}
