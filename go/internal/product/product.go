package product

import (
	"errors"
	"fmt"
	"sort"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/diagnostics"
	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
	"github.com/mikeyphw/dustui/go/internal/query"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/shell"
)

// Product identity describes supported runtime semantics, not a past migration
// gate, a hosted build result, a signed release, or device qualification.
const (
	Protocol               = "dustui.product.v2"
	AndroidBuildState      = "mandatory"
	AndroidRuntimeProtocol = "dustui.android.runtime.session.v1"
	SQLiteSchemaProtocol   = "dustui.sqlite.schema.v1"
)

type Surface string

const (
	SurfaceCLI     Surface = "go-cli"
	SurfaceTUI     Surface = "go-tui"
	SurfaceAndroid Surface = "android-shell"
)

type Workflow string

const (
	WorkflowScan        Workflow = "scan"
	WorkflowQuery       Workflow = "query-search"
	WorkflowActivity    Workflow = "status-activity"
	WorkflowCleanup     Workflow = "cleanup-review-capability-execution"
	WorkflowDiagnostics Workflow = "diagnostics-evidence"
	WorkflowSettings    Workflow = "settings-backend-selection"
	WorkflowExport      Workflow = "export-machine-readable"
)

type AndroidTaskFlow struct {
	Route               shell.RouteID `json:"route"`
	ContentAuthority    string        `json:"contentAuthority"`
	ActionAuthority     string        `json:"actionAuthority"`
	SharedStateModel    bool          `json:"sharedStateModel"`
	AccessibilityTarget bool          `json:"accessibilityTarget"`
}

type SurfaceContract struct {
	Name                    Surface    `json:"name"`
	ProductDefault          bool       `json:"productDefault"`
	MachineReadable         bool       `json:"machineReadable"`
	Interactive             bool       `json:"interactive"`
	PreservesDustTuiAlias   bool       `json:"preservesDustTuiAlias"`
	SupportedWorkflows      []Workflow `json:"supportedWorkflows"`
	UsesSameEngineSemantics bool       `json:"usesSameEngineSemantics"`
}

// Contract is a versioned description of implemented Go/Android interfaces.
// The deliberately absent release/device-qualified fields prevent a source
// build from reporting acceptance that only external execution can prove.
// Neither schema availability nor a supported surface proves runtime health.
type Contract struct {
	Protocol                     string               `json:"protocol"`
	AndroidBuildability          string               `json:"androidBuildability"`
	AndroidBuildabilityMandatory bool                 `json:"androidBuildabilityMandatory"`
	ProductionEngine             bool                 `json:"productionEngine"`
	GoSQLiteProductDefault       bool                 `json:"goSQLiteProductDefault"`
	HostAuthorityDefault         bool                 `json:"hostAuthorityDefault"`
	SourceOfTruth                string               `json:"sourceOfTruth"`
	AndroidRuntimeProtocol       string               `json:"androidRuntimeProtocol"`
	SQLiteSchemaProtocol         string               `json:"sqliteSchemaProtocol"`
	Routes                       []shell.RouteID      `json:"routes"`
	States                       []shell.ContentState `json:"states"`
	ActionGates                  []shell.ActionGate   `json:"actionGates"`
	Surfaces                     []SurfaceContract    `json:"surfaces"`
	AndroidTaskFlows             []AndroidTaskFlow    `json:"androidTaskFlows"`
	BoundProtocols               []string             `json:"boundProtocols"`
	DustTuiAliasImplementation   string               `json:"dustTuiAliasImplementation"`
}

func CurrentContract() Contract {
	shellContract := shell.CurrentContract()
	workflows := []Workflow{WorkflowScan, WorkflowQuery, WorkflowActivity, WorkflowCleanup, WorkflowDiagnostics, WorkflowSettings, WorkflowExport}
	routes := append([]shell.RouteID{}, shellContract.Routes...)
	flows := make([]AndroidTaskFlow, 0, len(routes))
	for _, route := range routes {
		content, action := authoritiesFor(route)
		flows = append(flows, AndroidTaskFlow{
			Route:               route,
			ContentAuthority:    content,
			ActionAuthority:     action,
			SharedStateModel:    true,
			AccessibilityTarget: true,
		})
	}
	return Contract{
		Protocol:                     Protocol,
		AndroidBuildability:          AndroidBuildState,
		AndroidBuildabilityMandatory: true,
		ProductionEngine:             true,
		GoSQLiteProductDefault:       true,
		HostAuthorityDefault:         true,
		SourceOfTruth:                "go-sqlite + go runtime protocols",
		AndroidRuntimeProtocol:       AndroidRuntimeProtocol,
		SQLiteSchemaProtocol:         SQLiteSchemaProtocol,
		Routes:                       routes,
		States:                       append([]shell.ContentState{}, shellContract.States...),
		ActionGates:                  append([]shell.ActionGate{}, shellContract.Gates...),
		Surfaces: []SurfaceContract{
			{Name: SurfaceCLI, ProductDefault: true, MachineReadable: true, Interactive: false, PreservesDustTuiAlias: true, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
			{Name: SurfaceTUI, ProductDefault: true, MachineReadable: false, Interactive: true, PreservesDustTuiAlias: true, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
			{Name: SurfaceAndroid, ProductDefault: true, MachineReadable: false, Interactive: true, PreservesDustTuiAlias: false, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
		},
		AndroidTaskFlows:           flows,
		BoundProtocols:             boundProtocols(),
		DustTuiAliasImplementation: "go-host-wrapper",
	}
}

func authoritiesFor(route shell.RouteID) (string, string) {
	domain, ok := shell.DomainFor(route)
	if !ok {
		return "unknown", "blocked"
	}
	switch domain {
	case shell.DomainOverview:
		return "go-evidence-health + go-query-summary", "go-scan-operation"
	case shell.DomainFiles:
		return "go-query + go-scan", "go-scan-operation"
	case shell.DomainClean:
		return "go-cleanup-intelligence + go-cleanup-manifest", "go-cleanup-capability-mutation"
	case shell.DomainApps:
		return "go-package-storage-evidence", "go-package-action-recheck"
	case shell.DomainActivity:
		return "go-automation-activity", "go-operation-cancel-reconcile"
	case shell.DomainSystem:
		return "go-diagnostics-evidence-privacy", "go-support-bundle"
	default:
		return "unknown", "blocked"
	}
}

func boundProtocols() []string {
	protocols := []string{
		Protocol,
		"dustui.kernel.v1",
		SQLiteSchemaProtocol,
		AndroidRuntimeProtocol,
		cleanup.Protocol,
		intelligence.Protocol,
		packageevidence.Protocol,
		diagnostics.Protocol,
		"dustui.automation.activity.v2",
	}
	sort.Strings(protocols)
	return protocols
}

func ValidateContract(c Contract) error {
	if c.Protocol != Protocol || c.AndroidRuntimeProtocol != AndroidRuntimeProtocol || c.SQLiteSchemaProtocol != SQLiteSchemaProtocol {
		return fmt.Errorf("wrong product/runtime contract identity: protocol=%q runtime=%q sqlite=%q", c.Protocol, c.AndroidRuntimeProtocol, c.SQLiteSchemaProtocol)
	}
	if !c.AndroidBuildabilityMandatory || c.AndroidBuildability != AndroidBuildState {
		return errors.New("Android build/test remains mandatory for hosted qualification")
	}
	if !c.ProductionEngine || !c.GoSQLiteProductDefault || !c.HostAuthorityDefault {
		return errors.New("Go host and SQLite must remain the default product authority")
	}
	if c.SourceOfTruth != "go-sqlite + go runtime protocols" || c.DustTuiAliasImplementation != "go-host-wrapper" {
		return errors.New("unexpected authority or host aliases")
	}
	if len(c.Routes) != 27 {
		return fmt.Errorf("expected 27 semantic routes; found %d", len(c.Routes))
	}
	if len(c.Surfaces) != 3 {
		return fmt.Errorf("expected 3 supported product surfaces; found %d", len(c.Surfaces))
	}
	required := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range c.Surfaces {
		if _, ok := required[surface.Name]; !ok || required[surface.Name] {
			return fmt.Errorf("unknown or duplicated product surface %q", surface.Name)
		}
		if !surface.ProductDefault || !surface.UsesSameEngineSemantics || len(surface.SupportedWorkflows) != 7 {
			return fmt.Errorf("surface %s lost supported Go semantics", surface.Name)
		}
		required[surface.Name] = true
	}
	for surface, seen := range required {
		if !seen {
			return fmt.Errorf("missing surface %s", surface)
		}
	}
	if len(c.AndroidTaskFlows) != len(c.Routes) {
		return errors.New("each semantic route requires an Android task flow")
	}
	routes := make(map[shell.RouteID]bool, len(c.Routes))
	for _, route := range c.Routes {
		if routes[route] {
			return fmt.Errorf("duplicate route %q", route)
		}
		routes[route] = true
	}
	seenFlows := make(map[shell.RouteID]bool, len(c.AndroidTaskFlows))
	for _, flow := range c.AndroidTaskFlows {
		if !routes[flow.Route] || seenFlows[flow.Route] || flow.ContentAuthority == "unknown" || flow.ActionAuthority == "blocked" || !flow.SharedStateModel || !flow.AccessibilityTarget {
			return fmt.Errorf("invalid or duplicate Android task flow %+v", flow)
		}
		seenFlows[flow.Route] = true
	}
	if len(c.BoundProtocols) < 8 || !contains(c.BoundProtocols, Protocol) || !contains(c.BoundProtocols, SQLiteSchemaProtocol) || !contains(c.BoundProtocols, AndroidRuntimeProtocol) {
		return errors.New("required runtime/protocol binding missing")
	}
	_ = query.MaxPageLimit
	_ = scan.IntegrityComplete
	return nil
}

func contains[T comparable](items []T, target T) bool {
	for _, item := range items {
		if item == target {
			return true
		}
	}
	return false
}
