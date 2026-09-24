package automation

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/mikeyphw/dustui/go/internal/operations"
	"github.com/mikeyphw/dustui/go/internal/protocol"
)

const Protocol = "dustui.automation.activity.v1"

const MaxNotificationActionTTL = 15 * time.Minute

type Adapter string

const (
	AdapterUI           Adapter = "ui"
	AdapterWorkManager  Adapter = "workmanager"
	AdapterTasker       Adapter = "tasker"
	AdapterNotification Adapter = "notification"
	AdapterRecovery     Adapter = "recovery"
)

type OperationKind string

const (
	OperationScan    OperationKind = "scan"
	OperationStatus  OperationKind = "status"
	OperationCancel  OperationKind = "cancel"
	OperationInspect OperationKind = "inspect"
)

type TerminalTruth string

const (
	TerminalPending          TerminalTruth = "pending"
	TerminalSuccess          TerminalTruth = "success"
	TerminalFailure          TerminalTruth = "failure"
	TerminalCancelled        TerminalTruth = "cancelled"
	TerminalInterrupted      TerminalTruth = "interrupted"
	TerminalRecoveryRequired TerminalTruth = "recovery_required"
)

type AdmissionRequest struct {
	Source            Adapter           `json:"source"`
	RequestID         string            `json:"requestId"`
	Kind              OperationKind     `json:"kind"`
	Generation        int64             `json:"generation"`
	TargetOperationID string            `json:"targetOperationId,omitempty"`
	Payload           map[string]string `json:"payload,omitempty"`
	NotificationNonce string            `json:"notificationNonce,omitempty"`
	Now               time.Time         `json:"-"`
}

type Admission struct {
	Accepted      bool                `json:"accepted"`
	Success       bool                `json:"success"`
	Queued        bool                `json:"queued"`
	Running       bool                `json:"running"`
	Terminal      bool                `json:"terminal"`
	OperationID   string              `json:"operationId"`
	RequestID     string              `json:"requestId"`
	Source        Adapter             `json:"source"`
	Kind          OperationKind       `json:"kind"`
	Generation    int64               `json:"generation"`
	State         operations.State    `json:"state"`
	TerminalTruth TerminalTruth       `json:"terminalTruth"`
	Reason        protocol.ReasonCode `json:"reason,omitempty"`
}

type NotificationAction struct {
	OperationID string    `json:"operationId"`
	Action      string    `json:"action"`
	Nonce       string    `json:"nonce"`
	ExpiresAt   time.Time `json:"expiresAt"`
	Claimed     bool      `json:"claimed"`
}

type Observation struct {
	OperationID      string             `json:"operationId"`
	State            operations.State   `json:"state"`
	Terminal         bool               `json:"terminal"`
	TerminalTruth    TerminalTruth      `json:"terminalTruth"`
	RecoveryRequired bool               `json:"recoveryRequired"`
	LastSequence     int64              `json:"lastSequence"`
	Events           []operations.Event `json:"events"`
}

type ReconcilePlan struct {
	OperationID        string `json:"operationId"`
	Required           bool   `json:"required"`
	Reason             string `json:"reason"`
	BlindReplayAllowed bool   `json:"blindReplayAllowed"`
}

type ActivityFilter string

const (
	ActivityAll      ActivityFilter = "all"
	ActivityActive   ActivityFilter = "active"
	ActivityHistory  ActivityFilter = "history"
	ActivityRecovery ActivityFilter = "recovery_required"
)

type ActivityRun struct {
	OperationID         string           `json:"operationId"`
	Kind                OperationKind    `json:"kind"`
	Sources             []Adapter        `json:"sources"`
	State               operations.State `json:"state"`
	Terminal            bool             `json:"terminal"`
	TerminalTruth       TerminalTruth    `json:"terminalTruth"`
	Progress            string           `json:"progress"`
	Result              string           `json:"result"`
	Verification        string           `json:"verification"`
	RecoveryRequired    bool             `json:"recoveryRequired"`
	CanRetry            bool             `json:"canRetry"`
	CanCancel           bool             `json:"canCancel"`
	CanReconcile        bool             `json:"canReconcile"`
	EvidenceCorrelation string           `json:"evidenceCorrelation"`
}

type ContractReport struct {
	Protocol                    string   `json:"protocol"`
	Phase                       string   `json:"phase"`
	GoAuthority                 bool     `json:"goAuthority"`
	AcceptedIsSuccess           bool     `json:"acceptedIsSuccess"`
	QueuedIsSuccess             bool     `json:"queuedIsSuccess"`
	RunningIsSuccess            bool     `json:"runningIsSuccess"`
	Adapters                    []string `json:"adapters"`
	AllowedAutomationOperations []string `json:"allowedAutomationOperations"`
	NotificationGuards          []string `json:"notificationGuards"`
	ActivitySurfaces            []string `json:"activitySurfaces"`
	SafetyRules                 []string `json:"safetyRules"`
}

type operationMeta struct {
	kind    OperationKind
	sources map[Adapter]bool
}

type Controller struct {
	mu      sync.Mutex
	store   *operations.Store
	ids     []string
	meta    map[string]*operationMeta
	actions map[string]*NotificationAction
}

func NewController() *Controller {
	return &Controller{store: operations.NewStore(512), meta: map[string]*operationMeta{}, actions: map[string]*NotificationAction{}}
}

func Contract() ContractReport {
	return ContractReport{
		Protocol:                    Protocol,
		Phase:                       "OV11",
		GoAuthority:                 true,
		AcceptedIsSuccess:           false,
		QueuedIsSuccess:             false,
		RunningIsSuccess:            false,
		Adapters:                    []string{string(AdapterUI), string(AdapterWorkManager), string(AdapterTasker), string(AdapterNotification), string(AdapterRecovery)},
		AllowedAutomationOperations: []string{string(OperationScan), string(OperationStatus), string(OperationCancel), string(OperationInspect)},
		NotificationGuards:          []string{"nonce", "expiry", "single_use", "operation_binding", "replay_rejection"},
		ActivitySurfaces:            []string{"active", "history", "run_details", "progress", "result", "verification", "recovery_required", "retry_gate", "cancel_gate", "reconcile_gate", "operation_evidence_correlation"},
		SafetyRules:                 []string{"accepted_not_success", "queued_not_success", "running_not_success", "thin_adapters", "process_death_does_not_orphan_job", "cancellation_is_operation_transition", "non_destructive_automation_only"},
	}
}

func (c *Controller) Admit(req AdmissionRequest) (Admission, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	now := req.Now
	if now.IsZero() {
		now = time.Now()
	}
	if err := validateRequest(req); err != nil {
		return Admission{}, err
	}
	if req.Source == AdapterNotification {
		if err := c.claimNotificationLocked(req, now); err != nil {
			return Admission{}, err
		}
	}
	if req.Kind == OperationCancel {
		return c.cancelLocked(req)
	}

	payload := stablePayload(req.Kind, req.Payload)
	operationID := "op-" + shortHash([]byte(req.RequestID+":"+string(req.Kind)+":"+string(payload)))
	op, err := c.store.Admit(operationID, operations.Request{RequestID: req.RequestID, Generation: req.Generation, Payload: payload})
	if err != nil {
		return Admission{}, err
	}
	c.rememberLocked(op.ID, req.Kind, req.Source)
	return admissionFromOperation(req, op), nil
}

func (c *Controller) Report(operationID string, state operations.State, reason protocol.ReasonCode, message string) (Observation, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	op, ok := c.store.Operation(operationID)
	if !ok {
		return Observation{}, protocol.NewError(protocol.ReasonInvalidRequest, "operation not found")
	}
	updated, err := c.store.Apply(op.Generation, operations.Event{OperationID: operationID, Sequence: op.LastSequence + 1, Generation: op.Generation, State: state, Reason: reason, Message: message})
	if err != nil {
		return observe(updated), err
	}
	return observe(updated), nil
}

func (c *Controller) Observe(operationID string) (Observation, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	op, ok := c.store.Operation(operationID)
	if !ok {
		return Observation{}, protocol.NewError(protocol.ReasonInvalidRequest, "operation not found")
	}
	return observe(op), nil
}

func (c *Controller) NotificationAction(operationID, action string, now time.Time, ttl time.Duration) (NotificationAction, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if strings.TrimSpace(operationID) == "" || strings.TrimSpace(action) == "" {
		return NotificationAction{}, protocol.NewError(protocol.ReasonInvalidRequest, "operation and action are required")
	}
	if ttl <= 0 || ttl > MaxNotificationActionTTL {
		return NotificationAction{}, protocol.NewError(protocol.ReasonInvalidRequest, "notification action ttl outside allowed bounds")
	}
	if now.IsZero() {
		now = time.Now()
	}
	nonce := shortHash([]byte(fmt.Sprintf("%s:%s:%d:%d", operationID, action, now.UnixNano(), len(c.actions))))
	created := &NotificationAction{OperationID: operationID, Action: action, Nonce: nonce, ExpiresAt: now.Add(ttl)}
	c.actions[nonce] = created
	return *created, nil
}

func (c *Controller) ReconcileAfterProcessDeath(operationID string) ReconcilePlan {
	obs, err := c.Observe(operationID)
	if err != nil {
		return ReconcilePlan{OperationID: operationID, Required: true, Reason: "operation_missing", BlindReplayAllowed: false}
	}
	if obs.Terminal {
		return ReconcilePlan{OperationID: operationID, Required: false, Reason: "terminal_truth_recorded", BlindReplayAllowed: false}
	}
	return ReconcilePlan{OperationID: operationID, Required: true, Reason: "non_terminal_operation_survived_process_death", BlindReplayAllowed: false}
}

func (c *Controller) Activity(filter ActivityFilter) []ActivityRun {
	c.mu.Lock()
	defer c.mu.Unlock()
	runs := []ActivityRun{}
	for _, id := range c.ids {
		op, ok := c.store.Operation(id)
		if !ok {
			continue
		}
		run := c.activityRunLocked(op)
		if includeActivity(filter, run) {
			runs = append(runs, run)
		}
	}
	return runs
}

func validateRequest(req AdmissionRequest) error {
	if !validAdapter(req.Source) || !validOperation(req.Kind) || req.RequestID == "" || req.Generation <= 0 {
		return protocol.NewError(protocol.ReasonInvalidRequest, "automation request missing source, kind, request id, or generation")
	}
	if req.Kind == OperationCancel && strings.TrimSpace(req.TargetOperationID) == "" {
		return protocol.NewError(protocol.ReasonInvalidRequest, "cancel requires target operation")
	}
	if req.Kind != OperationCancel && req.Source == AdapterNotification {
		return protocol.NewError(protocol.ReasonInvalidRequest, "notification automation is limited to status/cancel style actions")
	}
	return nil
}

func validAdapter(adapter Adapter) bool {
	switch adapter {
	case AdapterUI, AdapterWorkManager, AdapterTasker, AdapterNotification, AdapterRecovery:
		return true
	default:
		return false
	}
}

func validOperation(kind OperationKind) bool {
	switch kind {
	case OperationScan, OperationStatus, OperationCancel, OperationInspect:
		return true
	default:
		return false
	}
}

func (c *Controller) claimNotificationLocked(req AdmissionRequest, now time.Time) error {
	action := c.actions[req.NotificationNonce]
	if action == nil {
		return protocol.NewError(protocol.ReasonInvalidRequest, "notification nonce not found")
	}
	if action.Claimed {
		return protocol.NewError(protocol.ReasonStateConflict, "notification action already used")
	}
	if !now.Before(action.ExpiresAt) {
		return protocol.NewError(protocol.ReasonInvalidRequest, "notification action expired")
	}
	if req.TargetOperationID != "" && action.OperationID != req.TargetOperationID {
		return protocol.NewError(protocol.ReasonStateConflict, "notification action is bound to a different operation")
	}
	if action.Action != string(req.Kind) {
		return protocol.NewError(protocol.ReasonStateConflict, "notification action kind mismatch")
	}
	action.Claimed = true
	return nil
}

func (c *Controller) cancelLocked(req AdmissionRequest) (Admission, error) {
	op, ok := c.store.Operation(req.TargetOperationID)
	if !ok {
		return Admission{}, protocol.NewError(protocol.ReasonInvalidRequest, "target operation not found")
	}
	c.rememberLocked(op.ID, OperationCancel, req.Source)
	updated, err := c.store.Apply(op.Generation, operations.Event{OperationID: op.ID, Sequence: op.LastSequence + 1, Generation: op.Generation, State: operations.StateCancelling, Reason: protocol.ReasonStateConflict, Message: "cancellation requested"})
	if err != nil {
		return Admission{}, err
	}
	adm := admissionFromOperation(req, updated)
	adm.OperationID = op.ID
	adm.Kind = OperationCancel
	return adm, nil
}

func (c *Controller) rememberLocked(operationID string, kind OperationKind, source Adapter) {
	if _, ok := c.meta[operationID]; !ok {
		c.ids = append(c.ids, operationID)
		c.meta[operationID] = &operationMeta{kind: kind, sources: map[Adapter]bool{}}
	}
	if kind != OperationCancel {
		c.meta[operationID].kind = kind
	}
	c.meta[operationID].sources[source] = true
}

func (c *Controller) activityRunLocked(op operations.Operation) ActivityRun {
	obs := observe(op)
	meta := c.meta[op.ID]
	sources := []Adapter{}
	kind := OperationInspect
	if meta != nil {
		kind = meta.kind
		for s := range meta.sources {
			sources = append(sources, s)
		}
		sort.Slice(sources, func(i, j int) bool { return sources[i] < sources[j] })
	}
	run := ActivityRun{
		OperationID:         op.ID,
		Kind:                kind,
		Sources:             sources,
		State:               obs.State,
		Terminal:            obs.Terminal,
		TerminalTruth:       obs.TerminalTruth,
		Progress:            progressFor(op.State),
		Result:              resultFor(obs.TerminalTruth),
		Verification:        verificationFor(op.State),
		RecoveryRequired:    obs.RecoveryRequired,
		CanRetry:            obs.TerminalTruth == TerminalFailure || obs.TerminalTruth == TerminalInterrupted,
		CanCancel:           !obs.Terminal && op.State != operations.StateCancelling,
		CanReconcile:        obs.RecoveryRequired || !obs.Terminal,
		EvidenceCorrelation: op.Fingerprint,
	}
	return run
}

func admissionFromOperation(req AdmissionRequest, op operations.Operation) Admission {
	obs := observe(op)
	return Admission{Accepted: true, Success: false, Queued: op.State == operations.StateQueued, Running: op.State == operations.StateRunning, Terminal: obs.Terminal, OperationID: op.ID, RequestID: req.RequestID, Source: req.Source, Kind: req.Kind, Generation: op.Generation, State: op.State, TerminalTruth: obs.TerminalTruth}
}

func observe(op operations.Operation) Observation {
	terminal := op.State.Terminal()
	return Observation{OperationID: op.ID, State: op.State, Terminal: terminal, TerminalTruth: terminalTruth(op.State), RecoveryRequired: op.RecoveryRequired || op.State == operations.StateRecoveryRequired, LastSequence: op.LastSequence, Events: append([]operations.Event(nil), op.Events...)}
}

func terminalTruth(state operations.State) TerminalTruth {
	switch state {
	case operations.StateSuccess:
		return TerminalSuccess
	case operations.StateFailure:
		return TerminalFailure
	case operations.StateCancelled:
		return TerminalCancelled
	case operations.StateInterrupted:
		return TerminalInterrupted
	case operations.StateRecoveryRequired:
		return TerminalRecoveryRequired
	default:
		return TerminalPending
	}
}

func stablePayload(kind OperationKind, payload map[string]string) []byte {
	type pair struct{ K, V string }
	pairs := []pair{{K: "kind", V: string(kind)}}
	for k, v := range payload {
		pairs = append(pairs, pair{K: k, V: v})
	}
	sort.Slice(pairs, func(i, j int) bool { return pairs[i].K < pairs[j].K })
	encoded, _ := json.Marshal(pairs)
	return encoded
}

var nonID = regexp.MustCompile(`[^a-zA-Z0-9._-]+`)

func shortHash(payload []byte) string {
	sum := sha256.Sum256(payload)
	out := hex.EncodeToString(sum[:])[:20]
	return nonID.ReplaceAllString(out, "-")
}

func includeActivity(filter ActivityFilter, run ActivityRun) bool {
	switch filter {
	case "", ActivityAll:
		return true
	case ActivityActive:
		return !run.Terminal
	case ActivityHistory:
		return run.Terminal
	case ActivityRecovery:
		return run.RecoveryRequired || run.TerminalTruth == TerminalRecoveryRequired
	default:
		return false
	}
}

func progressFor(state operations.State) string {
	switch state {
	case operations.StateQueued:
		return "queued_not_success"
	case operations.StateRunning:
		return "running_not_success"
	case operations.StateCancelling:
		return "cancellation_transition_recorded"
	default:
		if state.Terminal() {
			return "terminal_truth_available"
		}
		return "pending"
	}
}

func resultFor(truth TerminalTruth) string {
	if truth == TerminalPending {
		return "not_terminal"
	}
	return string(truth)
}

func verificationFor(state operations.State) string {
	if state == operations.StateSuccess {
		return "verified_terminal_success"
	}
	if state.Terminal() {
		return "terminal_verification_recorded"
	}
	return "verification_pending"
}
