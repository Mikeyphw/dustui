package product

import (
	"errors"
	"fmt"
	"sort"

	"github.com/mikeyphw/dustui/go/internal/cleanup"
	"github.com/mikeyphw/dustui/go/internal/diagnostics"
	"github.com/mikeyphw/dustui/go/internal/intelligence"
	"github.com/mikeyphw/dustui/go/internal/packageevidence"
	"github.com/mikeyphw/dustui/go/internal/qualification"
	"github.com/mikeyphw/dustui/go/internal/query"
	"github.com/mikeyphw/dustui/go/internal/scan"
	"github.com/mikeyphw/dustui/go/internal/shell"
)

const (
	Phase             = "OV13"
	Protocol          = "dustui.product.cutover.v1"
	Gate              = "Gate D"
	AndroidBuildState = "mandatory"
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

type Contract struct {
	Phase                         string               `json:"phase"`
	Protocol                      string               `json:"protocol"`
	Gate                          string               `json:"gate"`
	AndroidBuildability           string               `json:"androidBuildability"`
	AndroidBuildabilityMandatory  bool                 `json:"androidBuildabilityMandatory"`
	ProductionEngine              bool                 `json:"productionEngine"`
	GoSQLiteProductDefault        bool                 `json:"goSQLiteProductDefault"`
	HostAuthorityDefault          bool                 `json:"hostAuthorityDefault"`
	LegacyDomainEnginesRequired   bool                 `json:"legacyDomainEnginesRequired"`
	LegacyPythonTextualProduction bool                 `json:"legacyPythonTextualProduction"`
	PythonParityOraclePresent     bool                 `json:"pythonParityOraclePresent"`
	SourceOfTruth                 string               `json:"sourceOfTruth"`
	Routes                        []shell.RouteID      `json:"routes"`
	States                        []shell.ContentState `json:"states"`
	ActionGates                   []shell.ActionGate   `json:"actionGates"`
	Surfaces                      []SurfaceContract    `json:"surfaces"`
	AndroidTaskFlows              []AndroidTaskFlow    `json:"androidTaskFlows"`
	RequiredEvidence              []string             `json:"requiredEvidence"`
	BoundProtocols                []string             `json:"boundProtocols"`
	FrozenPythonParityOracle      bool                 `json:"frozenPythonParityOracle"`
	DustTuiAliasImplementation    string               `json:"dustTuiAliasImplementation"`
	DeclarationScope              string               `json:"declarationScope"`
	GateDQualified                bool                 `json:"gateDQualified"`
	AndroidRuntimeBound           bool                 `json:"androidRuntimeBound"`
	GoSQLiteImplemented           bool                 `json:"goSQLiteImplemented"`
	ObservedAndroidAuthority      string               `json:"observedAndroidAuthority"`
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
		Phase:                         Phase,
		Protocol:                      Protocol,
		Gate:                          Gate,
		AndroidBuildability:           AndroidBuildState,
		AndroidBuildabilityMandatory:  true,
		ProductionEngine:              true,
		GoSQLiteProductDefault:        true,
		HostAuthorityDefault:          true,
		LegacyDomainEnginesRequired:   qualification.RoomArtifactsPresent,
		LegacyPythonTextualProduction: false,
		PythonParityOraclePresent:     qualification.PythonTextualOraclePresent,
		SourceOfTruth:                 "go-sqlite + go runtime protocols",
		Routes:                        routes,
		States:                        append([]shell.ContentState{}, shellContract.States...),
		ActionGates:                   append([]shell.ActionGate{}, shellContract.Gates...),
		Surfaces: []SurfaceContract{
			{Name: SurfaceCLI, ProductDefault: true, MachineReadable: true, Interactive: false, PreservesDustTuiAlias: true, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
			{Name: SurfaceTUI, ProductDefault: true, MachineReadable: false, Interactive: true, PreservesDustTuiAlias: true, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
			{Name: SurfaceAndroid, ProductDefault: qualification.GateDQualified, MachineReadable: false, Interactive: true, PreservesDustTuiAlias: false, SupportedWorkflows: workflows, UsesSameEngineSemantics: true},
		},
		AndroidTaskFlows:           flows,
		RequiredEvidence:           append([]string{}, qualification.RequiredEvidenceNodes...),
		BoundProtocols:             boundProtocols(),
		FrozenPythonParityOracle:   true,
		DustTuiAliasImplementation: "go-host-wrapper",
		DeclarationScope:           "canonical-evidence-derived:" + qualification.StateSource,
		GateDQualified:             qualification.GateDQualified,
		AndroidRuntimeBound:        qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent,
		GoSQLiteImplemented:        qualification.GoSQLiteImplementationPresent,
		ObservedAndroidAuthority:   observedAndroidAuthority(),
	}
}

func authoritiesFor(route shell.RouteID) (string, string) {
	switch route {
	case shell.RouteOverview:
		return "go-evidence-health + go-query-summary", "go-scan-operation"
	case shell.RouteFiles:
		return "go-query + go-scan", "go-scan-operation"
	case shell.RouteClean:
		return "go-cleanup-intelligence + go-cleanup-manifest", "go-cleanup-capability-mutation"
	case shell.RouteApps:
		return "go-package-storage-evidence", "go-package-action-recheck"
	case shell.RouteActivity:
		return "go-automation-activity", "go-operation-cancel-reconcile"
	case shell.RouteSystem:
		return "go-diagnostics-evidence-privacy", "go-support-bundle"
	default:
		return "unknown", "blocked"
	}
}

func observedAndroidAuthority() string {
	if qualification.GateDQualified {
		return "go-product-qualified"
	}
	if qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent && qualification.GoSQLiteImplementationPresent {
		return "go-runtime-and-sqlite-source-present-gate-d-unqualified"
	}
	return "successor-runtime-not-yet-qualified"
}

func boundProtocols() []string {
	protocols := []string{
		Protocol,
		"dustui.kernel.v1",
		"dustui.sqlite.schema.v1",
		"dustui.android.runtime.session.v1",
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
	if c.Phase != Phase || c.Protocol != Protocol || c.Gate != Gate {
		return fmt.Errorf("wrong product contract identity: %+v", c)
	}
	if !c.AndroidBuildabilityMandatory || c.AndroidBuildability != AndroidBuildState {
		return errors.New("android buildability must be mandatory at OV13")
	}
	if c.GateDQualified != qualification.GateDQualified {
		return errors.New("Gate D qualification must come from canonical evidence")
	}
	if !c.ProductionEngine || !c.GoSQLiteProductDefault || !c.HostAuthorityDefault {
		return errors.New("AUD-FIX-07 requires the Go host and Go SQLite authority to be production-default")
	}
	if c.LegacyDomainEnginesRequired != qualification.RoomArtifactsPresent || c.LegacyPythonTextualProduction {
		return errors.New("Python/Textual cannot remain a production host after AUD-FIX-07")
	}
	if c.PythonParityOraclePresent != qualification.PythonTextualOraclePresent {
		return errors.New("Python/Textual oracle observation must come from canonical generated state")
	}
	if c.AndroidRuntimeBound != (qualification.AndroidGoProcessBindingPresent && qualification.AndroidGoPackagingPresent) || c.GoSQLiteImplemented != qualification.GoSQLiteImplementationPresent {
		return errors.New("runtime/persistence observations must come from canonical generated state")
	}
	if c.DeclarationScope != "canonical-evidence-derived:"+qualification.StateSource || c.ObservedAndroidAuthority == "" {
		return errors.New("product contract must identify canonical evidence-derived state")
	}
	if len(c.Routes) != 6 {
		return fmt.Errorf("expected six semantic routes, got %d", len(c.Routes))
	}
	requiredSurfaces := map[Surface]bool{SurfaceCLI: false, SurfaceTUI: false, SurfaceAndroid: false}
	for _, surface := range c.Surfaces {
		if _, ok := requiredSurfaces[surface.Name]; !ok {
			return fmt.Errorf("unexpected surface %q", surface.Name)
		}
		expectedDefault := true
		if surface.Name == SurfaceAndroid {
			expectedDefault = qualification.GateDQualified
		}
		if surface.ProductDefault != expectedDefault || !surface.UsesSameEngineSemantics {
			return fmt.Errorf("surface %s default/shared-engine truth is invalid", surface.Name)
		}
		if len(surface.SupportedWorkflows) < 7 {
			return fmt.Errorf("surface %s lacks supported workflows", surface.Name)
		}
		requiredSurfaces[surface.Name] = true
	}
	for surface, seen := range requiredSurfaces {
		if !seen {
			return fmt.Errorf("missing surface %s", surface)
		}
	}
	if len(c.AndroidTaskFlows) != len(c.Routes) {
		return errors.New("each route must have an Android task flow")
	}
	for _, flow := range c.AndroidTaskFlows {
		if flow.ContentAuthority == "unknown" || flow.ActionAuthority == "blocked" || !flow.SharedStateModel || !flow.AccessibilityTarget {
			return fmt.Errorf("invalid Android task flow: %+v", flow)
		}
	}
	if len(c.RequiredEvidence) != len(qualification.RequiredEvidenceNodes) {
		return errors.New("Gate D evidence set must come from canonical qualification registry")
	}
	for _, required := range qualification.RequiredEvidenceNodes {
		if !contains(c.RequiredEvidence, required) {
			return fmt.Errorf("Gate D evidence set missing %s", required)
		}
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
