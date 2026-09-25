package shell

import (
	"errors"
	"fmt"
	"strings"
)

// RouteBinding is the canonical async identity for a content-route request.
// A response may update presentation state only while every populated identity
// still matches the active request.
type RouteBinding struct {
	RouteKey        RouteID `json:"routeKey"`
	RouteGeneration int64   `json:"routeGeneration"`
	EntityKind      string  `json:"entityKind,omitempty"`
	EntityID        string  `json:"entityId,omitempty"`
	RequestID       string  `json:"requestId"`
	Fingerprint     string  `json:"fingerprint,omitempty"`
}

type RemediationAction struct {
	Code     string  `json:"code"`
	Label    string  `json:"label"`
	RouteKey RouteID `json:"routeKey,omitempty"`
}

// ActionGate is intentionally a structured Go-owned decision. Android renders
// these fields and does not recreate the policy from local booleans.
type ActionGate struct {
	Allowed            bool                `json:"allowed"`
	ReasonCode         string              `json:"reasonCode"`
	Message            string              `json:"message"`
	RemediationActions []RemediationAction `json:"remediationActions"`
}

type GateContext struct {
	Action              string `json:"action"`
	HasTrustedScan      bool   `json:"hasTrustedScan"`
	ScanFresh           bool   `json:"scanFresh"`
	CapabilityAvailable bool   `json:"capabilityAvailable"`
	EvidenceSupported   bool   `json:"evidenceSupported"`
	OperationActive     bool   `json:"operationActive"`
	RecoveryRequired    bool   `json:"recoveryRequired"`
	ScopePath           string `json:"scopePath,omitempty"`
	IntegrityStatus     string `json:"integrityStatus,omitempty"`
	PreflightChecked    bool   `json:"preflightChecked,omitempty"`
	PreflightPassed     bool   `json:"preflightPassed,omitempty"`
}

type ScreenState struct {
	Kind            ContentState       `json:"kind"`
	Binding         RouteBinding       `json:"binding"`
	ReasonCode      string             `json:"reasonCode,omitempty"`
	Message         string             `json:"message,omitempty"`
	ProblemRef      string             `json:"problemRef,omitempty"`
	Freshness       string             `json:"freshness,omitempty"`
	PrimaryRecovery *RemediationAction `json:"primaryRecovery,omitempty"`
	ActionGates     []ActionGate       `json:"actionGates"`
}

type ScreenStateRequest struct {
	Binding         RouteBinding       `json:"binding"`
	Kind            ContentState       `json:"kind"`
	ReasonCode      string             `json:"reasonCode,omitempty"`
	Message         string             `json:"message,omitempty"`
	ProblemRef      string             `json:"problemRef,omitempty"`
	Freshness       string             `json:"freshness,omitempty"`
	PrimaryRecovery *RemediationAction `json:"primaryRecovery,omitempty"`
	Gates           []GateContext      `json:"gates,omitempty"`
}

func NewBinding(route Route, requestID, fingerprint string) (RouteBinding, error) {
	requestID = strings.TrimSpace(requestID)
	fingerprint = strings.TrimSpace(fingerprint)
	if requestID == "" {
		return RouteBinding{}, errors.New("request_id_required")
	}
	spec, ok := specFor(route.ID)
	if !ok {
		return RouteBinding{}, fmt.Errorf("unknown_route:%s", route.ID)
	}
	if route.Generation <= 0 {
		return RouteBinding{}, errors.New("route_generation_required")
	}
	if spec.EntityKind != route.EntityKind {
		return RouteBinding{}, errors.New("entity_kind_mismatch")
	}
	if spec.EntityKind != "" && strings.TrimSpace(route.EntityID) == "" {
		return RouteBinding{}, errors.New("entity_id_required")
	}
	if spec.EntityKind == "" && strings.TrimSpace(route.EntityID) != "" {
		return RouteBinding{}, errors.New("entity_id_not_allowed")
	}
	return RouteBinding{
		RouteKey:        route.ID,
		RouteGeneration: route.Generation,
		EntityKind:      route.EntityKind,
		EntityID:        route.EntityID,
		RequestID:       requestID,
		Fingerprint:     fingerprint,
	}, nil
}

func ValidateBinding(binding RouteBinding) error {
	route, err := Restore(binding.RouteKey, binding.EntityID, binding.RouteGeneration)
	if err != nil {
		return err
	}
	if route.EntityKind != binding.EntityKind {
		return errors.New("entity_kind_mismatch")
	}
	if strings.TrimSpace(binding.RequestID) == "" {
		return errors.New("request_id_required")
	}
	return nil
}

func AcceptBinding(active, response RouteBinding) error {
	if err := ValidateBinding(active); err != nil {
		return fmt.Errorf("active_binding_invalid:%w", err)
	}
	if err := ValidateBinding(response); err != nil {
		return fmt.Errorf("response_binding_invalid:%w", err)
	}
	if active.RouteKey != response.RouteKey ||
		active.RouteGeneration != response.RouteGeneration ||
		active.EntityKind != response.EntityKind ||
		active.EntityID != response.EntityID ||
		active.RequestID != response.RequestID ||
		active.Fingerprint != response.Fingerprint {
		return errors.New("stale_or_cross_route_response")
	}
	return nil
}

func allowedGate() ActionGate {
	return ActionGate{Allowed: true, ReasonCode: "allowed", Message: "Available", RemediationActions: []RemediationAction{}}
}

func blockedGate(code, message string, remediation ...RemediationAction) ActionGate {
	if remediation == nil {
		remediation = []RemediationAction{}
	}
	return ActionGate{Allowed: false, ReasonCode: code, Message: message, RemediationActions: remediation}
}

// Named examples retained for host/TUI compatibility; they are structured gates,
// not policy enums. Product actions should call EvaluateActionGate with live context.
var (
	GateAllowed         = allowedGate()
	GateBlocked         = blockedGate("blocked", "This action is currently blocked")
	GateNeedsFreshScan  = blockedGate("scan_stale", "The scan is stale and must be refreshed before this action", RemediationAction{Code: "start_scan", Label: "Run a new scan", RouteKey: RouteOverviewHome})
	GateNeedsCapability = blockedGate("capability_required", "The required capability is not currently available", RemediationAction{Code: "open_diagnostics", Label: "Open Diagnostics", RouteKey: RouteSystemDiagnostics})
)

// EvaluateActionGate centralizes risky/evidence-dependent product decisions.
// AUD-FIX-10 may add domain-specific conditions, but Android must consume this
// structured decision instead of cloning it.
func EvaluateActionGate(ctx GateContext) ActionGate {
	action := strings.TrimSpace(strings.ToLower(ctx.Action))
	if action == "" {
		return blockedGate("action_required", "This action has no authoritative identity")
	}
	if ctx.RecoveryRequired && action != "activity.reconcile" {
		return blockedGate("recovery_required", "Reconcile the interrupted operation before continuing",
			RemediationAction{Code: "open_activity", Label: "Open Activity", RouteKey: RouteActivityList})
	}
	if action == "scan.start" {
		if !ctx.CapabilityAvailable {
			return blockedGate("root_unavailable", "Root access is unavailable, so this scan cannot start",
				RemediationAction{Code: "open_diagnostics", Label: "Open Diagnostics", RouteKey: RouteSystemDiagnostics})
		}
		if ctx.PreflightChecked && !ctx.PreflightPassed {
			return blockedGate("preflight_failed", "The selected scope failed privileged preflight and cannot start",
				RemediationAction{Code: "open_diagnostics", Label: "Review diagnostics", RouteKey: RouteSystemDiagnostics})
		}
	}
	if strings.HasPrefix(action, "apps.") && wrongAppsScope(ctx.ScopePath) {
		return blockedGate("wrong_scope", "The current scan scope does not contain app-storage evidence",
			RemediationAction{Code: "start_scan", Label: "Choose an app-storage scope", RouteKey: RouteOverviewHome})
	}
	if requiresTrustedIntegrity(action) && degradedIntegrity(ctx.IntegrityStatus) {
		return blockedGate("integrity_degraded", "The current scan has degraded integrity and is not safe for this action",
			RemediationAction{Code: "open_diagnostics", Label: "Review scan evidence", RouteKey: RouteSystemDiagnostics},
			RemediationAction{Code: "start_scan", Label: "Run a fresh scan", RouteKey: RouteOverviewHome})
	}
	requiresScan := strings.HasPrefix(action, "cleanup.") || strings.HasPrefix(action, "files.") || strings.HasPrefix(action, "apps.") || action == "scan.cancel" || action == "certification.run"
	if requiresScan && !ctx.HasTrustedScan {
		return blockedGate("scan_required", "A trusted scan is required before this action can run",
			RemediationAction{Code: "start_scan", Label: "Start scan", RouteKey: RouteOverviewHome})
	}
	requiresFresh := strings.HasPrefix(action, "cleanup.") || action == "files.export" || action == "apps.inspect"
	if requiresFresh && !ctx.ScanFresh {
		return blockedGate("scan_stale", "The scan is stale and must be refreshed before this action",
			RemediationAction{Code: "start_scan", Label: "Run a new scan", RouteKey: RouteOverviewHome})
	}
	requiresEvidence := strings.HasPrefix(action, "cleanup.") || strings.HasPrefix(action, "apps.") || action == "files.export" || action == "certification.run"
	if requiresEvidence && !ctx.EvidenceSupported {
		return blockedGate("evidence_unavailable", "Required evidence is unavailable on this device or scope",
			RemediationAction{Code: "open_diagnostics", Label: "Open Diagnostics", RouteKey: RouteSystemDiagnostics})
	}
	requiresCapability := action == "cleanup.execute" || action == "activity.cancel" || action == "activity.retry" || action == "activity.reconcile" || action == "scan.cancel"
	if requiresCapability && !ctx.CapabilityAvailable {
		return blockedGate("capability_required", "The required capability is not currently available",
			RemediationAction{Code: "open_diagnostics", Label: "Open Diagnostics", RouteKey: RouteSystemDiagnostics})
	}
	if (action == "activity.cancel" || action == "scan.cancel") && !ctx.OperationActive {
		return blockedGate("operation_not_active", "The operation is no longer active")
	}
	return allowedGate()
}

func wrongAppsScope(scope string) bool {
	scope = strings.TrimSpace(strings.ToLower(scope))
	if scope == "" {
		return false
	}
	return scope == "/data/adb/modules" || strings.HasPrefix(scope, "/data/adb/modules/") || scope == "/system" || strings.HasPrefix(scope, "/system/")
}

func degradedIntegrity(status string) bool {
	switch strings.ToUpper(strings.TrimSpace(status)) {
	case "TRUNCATED", "FAILED_INTEGRITY", "INTERRUPTED":
		return true
	default:
		return false
	}
}

func requiresTrustedIntegrity(action string) bool {
	return strings.HasPrefix(action, "cleanup.") || strings.HasPrefix(action, "apps.") || action == "files.export" || action == "certification.run"
}

func BuildScreenState(req ScreenStateRequest) (ScreenState, error) {
	if err := ValidateBinding(req.Binding); err != nil {
		return ScreenState{}, err
	}
	if !validState(req.Kind) {
		return ScreenState{}, fmt.Errorf("unknown_state:%s", req.Kind)
	}
	state := ScreenState{
		Kind:            req.Kind,
		Binding:         req.Binding,
		ReasonCode:      strings.TrimSpace(req.ReasonCode),
		Message:         strings.TrimSpace(req.Message),
		ProblemRef:      strings.TrimSpace(req.ProblemRef),
		Freshness:       strings.TrimSpace(req.Freshness),
		PrimaryRecovery: req.PrimaryRecovery,
		ActionGates:     make([]ActionGate, 0, len(req.Gates)),
	}
	for _, gateReq := range req.Gates {
		state.ActionGates = append(state.ActionGates, EvaluateActionGate(gateReq))
	}
	switch req.Kind {
	case StateLoading:
		state.ReasonCode = ""
		state.ProblemRef = ""
	case StateEmpty:
		if state.ReasonCode == "" || state.PrimaryRecovery == nil {
			return ScreenState{}, errors.New("empty_requires_reason_and_recovery")
		}
	case StateBlocked:
		if len(state.ActionGates) == 0 {
			return ScreenState{}, errors.New("blocked_requires_action_gate")
		}
		var authoritative *ActionGate
		for i := range state.ActionGates {
			if !state.ActionGates[i].Allowed {
				authoritative = &state.ActionGates[i]
				break
			}
		}
		if authoritative == nil {
			return ScreenState{}, errors.New("blocked_requires_denied_action_gate")
		}
		state.ReasonCode = authoritative.ReasonCode
		state.Message = authoritative.Message
		if state.PrimaryRecovery == nil && len(authoritative.RemediationActions) > 0 {
			recovery := authoritative.RemediationActions[0]
			state.PrimaryRecovery = &recovery
		}
	case StateError:
		if state.ProblemRef == "" {
			return ScreenState{}, errors.New("error_requires_problem_reference")
		}
	case StateContent:
		if state.Freshness == "" {
			state.Freshness = "unknown"
		}
	}
	return state, nil
}
