package privileged

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"sort"
	"sync"
	"time"

	"github.com/mikeyphw/dustui/go/internal/protocol"
	"github.com/mikeyphw/dustui/go/internal/version"
)

const (
	ProtocolName             = "dustui.privileged.runtime.v1"
	DefaultRootNamespace     = "dustui.root.v1"
	DefaultRuntimeIdentity   = "dustui-go-root-runtime"
	DefaultMaxPayloadBytes   = 64 * 1024
	DefaultMaxResponseBytes  = 64 * 1024
	DefaultMaxEventBytes     = 32 * 1024
	DefaultMaxQueueDepth     = 4
	DefaultMaxWorkerCount    = 2
	CapabilityReadOnly       = CapabilityClass("read_only")
	CapabilityPrivilegedRead = CapabilityClass("privileged_read")
	CapabilityDryRunMutation = CapabilityClass("dry_run_mutation")
	CapabilityDestructive    = CapabilityClass("destructive")
	ClassReadOnly            = OperationClass("read_only")
	ClassDestructive         = OperationClass("destructive")
)

var requestIDPattern = regexp.MustCompile(`^[A-Za-z0-9._:-]{3,96}$`)

type CapabilityClass string

type OperationClass string

type Decision string

const (
	DecisionAdmitted Decision = "admitted"
	DecisionRejected Decision = "rejected"
)

type Status string

const (
	StatusQueued            Status = "queued"
	StatusRunning           Status = "running"
	StatusCompleted         Status = "completed"
	StatusRejected          Status = "rejected"
	StatusReconcileRequired Status = "reconcile_required"
	StatusRestartBlocked    Status = "restart_blocked_no_replay"
)

type Reason string

const (
	ReasonInvalidJSON          Reason = "invalid_json"
	ReasonUnsupportedProtocol  Reason = "unsupported_protocol"
	ReasonInvalidRequestID     Reason = "invalid_request_id"
	ReasonUnknownOperation     Reason = "unknown_operation"
	ReasonUnknownPayloadField  Reason = "unknown_payload_field"
	ReasonUnauthorized         Reason = "unauthorized_capability"
	ReasonWrongScope           Reason = "wrong_scope"
	ReasonPayloadTooLarge      Reason = "payload_too_large"
	ReasonResponseTooLarge     Reason = "response_too_large"
	ReasonEventTooLarge        Reason = "event_too_large"
	ReasonRuntimeIdentity      Reason = "runtime_identity_mismatch"
	ReasonRootNamespace        Reason = "root_namespace_mismatch"
	ReasonQueueExhausted       Reason = "queue_exhausted"
	ReasonDestructiveSingleRun Reason = "destructive_single_flight"
	ReasonLostTransport        Reason = "lost_transport_reconcile_required"
	ReasonRestartNoReplay      Reason = "restart_no_replay"
)

type Error struct {
	Reason  Reason `json:"reason"`
	Message string `json:"message"`
}

func (e Error) Error() string {
	if e.Message == "" {
		return string(e.Reason)
	}
	return string(e.Reason) + ": " + e.Message
}

func err(reason Reason, message string) Error { return Error{Reason: reason, Message: message} }

type Request struct {
	Protocol          string          `json:"protocol"`
	RequestID         string          `json:"requestId"`
	Operation         string          `json:"operation"`
	CapabilityClass   CapabilityClass `json:"capabilityClass"`
	TargetScope       string          `json:"targetScope"`
	RootNamespace     string          `json:"rootNamespace"`
	RuntimeIdentity   string          `json:"runtimeIdentity"`
	Payload           json.RawMessage `json:"payload"`
	TransportEpoch    int64           `json:"transportEpoch,omitempty"`
	ExpectedBootID    string          `json:"expectedBootId,omitempty"`
	ExpectedProcessID string          `json:"expectedProcessId,omitempty"`
}

type OperationSpec struct {
	Name                 string            `json:"name"`
	Class                OperationClass    `json:"class"`
	CapabilityClasses    []CapabilityClass `json:"capabilityClasses"`
	AllowedPayloadFields []string          `json:"allowedPayloadFields"`
	Description          string            `json:"description"`
}

type BootIdentity struct {
	BootID    string `json:"bootId"`
	ProcessID string `json:"processId"`
	StartedMs int64  `json:"startedMs"`
}

type Config struct {
	RootNamespace    string
	RuntimeIdentity  string
	AllowedScopes    []string
	Operations       []OperationSpec
	MaxPayloadBytes  int
	MaxResponseBytes int
	MaxEventBytes    int
	MaxQueueDepth    int
	MaxWorkers       int
	Boot             BootIdentity
}

type Admission struct {
	Decision      Decision       `json:"decision"`
	OperationID   string         `json:"operationId,omitempty"`
	RequestID     string         `json:"requestId"`
	Operation     string         `json:"operation"`
	Class         OperationClass `json:"class,omitempty"`
	Reason        Reason         `json:"reason,omitempty"`
	Message       string         `json:"message,omitempty"`
	TargetScope   string         `json:"targetScope,omitempty"`
	QueueDepth    int            `json:"queueDepth"`
	Destructive   bool           `json:"destructive"`
	NoReplayToken string         `json:"noReplayToken,omitempty"`
}

type Record struct {
	OperationID       string          `json:"operationId"`
	RequestID         string          `json:"requestId"`
	RequestHash       string          `json:"requestHash"`
	Operation         string          `json:"operation"`
	Class             OperationClass  `json:"class"`
	CapabilityClass   CapabilityClass `json:"capabilityClass"`
	TargetScope       string          `json:"targetScope"`
	Status            Status          `json:"status"`
	Reason            Reason          `json:"reason,omitempty"`
	Message           string          `json:"message,omitempty"`
	TransportEpoch    int64           `json:"transportEpoch"`
	Boot              BootIdentity    `json:"boot"`
	NoReplayToken     string          `json:"noReplayToken"`
	CreatedUnixMs     int64           `json:"createdUnixMs"`
	UpdatedUnixMs     int64           `json:"updatedUnixMs"`
	ReconcileRequired bool            `json:"reconcileRequired"`
}

type Diagnostics struct {
	Protocol           string          `json:"protocol"`
	KernelProtocol     string          `json:"kernelProtocol"`
	RootNamespace      string          `json:"rootNamespace"`
	RuntimeIdentity    string          `json:"runtimeIdentity"`
	Boot               BootIdentity    `json:"boot"`
	AllowedScopes      []string        `json:"allowedScopes"`
	Operations         []OperationSpec `json:"operations"`
	MaxPayloadBytes    int             `json:"maxPayloadBytes"`
	MaxResponseBytes   int             `json:"maxResponseBytes"`
	MaxEventBytes      int             `json:"maxEventBytes"`
	MaxQueueDepth      int             `json:"maxQueueDepth"`
	MaxWorkers         int             `json:"maxWorkers"`
	Queued             int             `json:"queued"`
	RunningDestructive bool            `json:"runningDestructive"`
	GenericShell       bool            `json:"genericShell"`
	ProductionMutation bool            `json:"productionMutation"`
}

type Controller struct {
	mu                 sync.Mutex
	cfg                Config
	operationSpecs     map[string]OperationSpec
	scopes             map[string]struct{}
	records            map[string]Record
	requestIndex       map[string]string
	queued             int
	runningDestructive bool
}

func DefaultOperationSpecs() []OperationSpec {
	return []OperationSpec{
		{Name: "root.readiness", Class: ClassReadOnly, CapabilityClasses: []CapabilityClass{CapabilityReadOnly, CapabilityPrivilegedRead}, AllowedPayloadFields: []string{"includeDiagnostics"}, Description: "privileged readiness/scope diagnostics"},
		{Name: "root.status", Class: ClassReadOnly, CapabilityClasses: []CapabilityClass{CapabilityReadOnly, CapabilityPrivilegedRead}, AllowedPayloadFields: []string{"operationId"}, Description: "durable privileged status lookup"},
		{Name: "root.reconcile", Class: ClassReadOnly, CapabilityClasses: []CapabilityClass{CapabilityPrivilegedRead}, AllowedPayloadFields: []string{"operationId", "transportEpoch", "lastSeenSequence"}, Description: "durable reconcile after lost transport/process death"},
		{Name: "cleanup.preview", Class: ClassReadOnly, CapabilityClasses: []CapabilityClass{CapabilityDryRunMutation}, AllowedPayloadFields: []string{"manifestId", "generation", "scope"}, Description: "dry-run cleanup preview only; no mutation"},
		{Name: "cleanup.execute.dry_run", Class: ClassDestructive, CapabilityClasses: []CapabilityClass{CapabilityDryRunMutation}, AllowedPayloadFields: []string{"manifestId", "generation", "scope", "dryRun"}, Description: "destructive-class admission simulator"},
		{Name: "cleanup.execute", Class: ClassDestructive, CapabilityClasses: []CapabilityClass{CapabilityDestructive}, AllowedPayloadFields: []string{"manifestId", "manifestHash", "capabilityId", "operationId", "generation", "scope"}, Description: "production cleanup mutation through Go manifest/capability executor"},
	}
}

func DefaultConfig() Config {
	now := time.Now().UnixMilli()
	return Config{
		RootNamespace:    DefaultRootNamespace,
		RuntimeIdentity:  DefaultRuntimeIdentity,
		AllowedScopes:    []string{"app-owned-cache", "app-owned-runtime", "android-data-readonly", "cleanup-dry-run", "cleanup-production"},
		Operations:       DefaultOperationSpecs(),
		MaxPayloadBytes:  DefaultMaxPayloadBytes,
		MaxResponseBytes: DefaultMaxResponseBytes,
		MaxEventBytes:    DefaultMaxEventBytes,
		MaxQueueDepth:    DefaultMaxQueueDepth,
		MaxWorkers:       DefaultMaxWorkerCount,
		Boot:             BootIdentity{BootID: fmt.Sprintf("boot-%d", now), ProcessID: fmt.Sprintf("pid-%d", now), StartedMs: now},
	}
}

func NewController(cfg Config) (*Controller, error) {
	if cfg.RootNamespace == "" {
		cfg.RootNamespace = DefaultRootNamespace
	}
	if cfg.RuntimeIdentity == "" {
		cfg.RuntimeIdentity = DefaultRuntimeIdentity
	}
	if cfg.MaxPayloadBytes <= 0 {
		cfg.MaxPayloadBytes = DefaultMaxPayloadBytes
	}
	if cfg.MaxResponseBytes <= 0 {
		cfg.MaxResponseBytes = DefaultMaxResponseBytes
	}
	if cfg.MaxEventBytes <= 0 {
		cfg.MaxEventBytes = DefaultMaxEventBytes
	}
	if cfg.MaxQueueDepth <= 0 {
		cfg.MaxQueueDepth = DefaultMaxQueueDepth
	}
	if cfg.MaxWorkers <= 0 {
		cfg.MaxWorkers = DefaultMaxWorkerCount
	}
	if cfg.Boot.StartedMs == 0 {
		cfg.Boot = DefaultConfig().Boot
	}
	if len(cfg.AllowedScopes) == 0 {
		cfg.AllowedScopes = DefaultConfig().AllowedScopes
	}
	if len(cfg.Operations) == 0 {
		cfg.Operations = DefaultOperationSpecs()
	}
	specs := map[string]OperationSpec{}
	for _, spec := range cfg.Operations {
		if spec.Name == "" || spec.Class == "" || len(spec.CapabilityClasses) == 0 {
			return nil, errors.New("privileged_operation_spec_incomplete")
		}
		if spec.Name == "shell" || spec.Name == "root.shell" || spec.Name == "exec" {
			return nil, errors.New("generic_shell_operation_forbidden")
		}
		specs[spec.Name] = spec
	}
	scopes := map[string]struct{}{}
	for _, scope := range cfg.AllowedScopes {
		if scope != "" {
			scopes[scope] = struct{}{}
		}
	}
	return &Controller{cfg: cfg, operationSpecs: specs, scopes: scopes, records: map[string]Record{}, requestIndex: map[string]string{}}, nil
}

func (c *Controller) Diagnostics() Diagnostics {
	c.mu.Lock()
	defer c.mu.Unlock()
	ops := make([]OperationSpec, 0, len(c.operationSpecs))
	for _, op := range c.operationSpecs {
		ops = append(ops, op)
	}
	sort.Slice(ops, func(i, j int) bool { return ops[i].Name < ops[j].Name })
	scopes := append([]string{}, c.cfg.AllowedScopes...)
	sort.Strings(scopes)
	return Diagnostics{Protocol: ProtocolName, KernelProtocol: version.Protocol, RootNamespace: c.cfg.RootNamespace, RuntimeIdentity: c.cfg.RuntimeIdentity, Boot: c.cfg.Boot, AllowedScopes: scopes, Operations: ops, MaxPayloadBytes: c.cfg.MaxPayloadBytes, MaxResponseBytes: c.cfg.MaxResponseBytes, MaxEventBytes: c.cfg.MaxEventBytes, MaxQueueDepth: c.cfg.MaxQueueDepth, MaxWorkers: c.cfg.MaxWorkers, Queued: c.queued, RunningDestructive: c.runningDestructive, GenericShell: false, ProductionMutation: true}
}

func (c *Controller) Admit(raw []byte) (Admission, error) {
	if len(raw) == 0 {
		return reject("", "", "", "", ReasonInvalidJSON, "empty privileged request"), err(ReasonInvalidJSON, "empty privileged request")
	}
	if len(raw) > c.cfg.MaxPayloadBytes {
		return reject("", "", "", "", ReasonPayloadTooLarge, "privileged request exceeds bounded payload size"), err(ReasonPayloadTooLarge, "privileged request exceeds bounded payload size")
	}
	var req Request
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.DisallowUnknownFields()
	if decodeErr := dec.Decode(&req); decodeErr != nil {
		return reject("", "", "", "", ReasonInvalidJSON, decodeErr.Error()), err(ReasonInvalidJSON, decodeErr.Error())
	}
	if req.Protocol != ProtocolName {
		return reject(req.RequestID, req.Operation, "", req.TargetScope, ReasonUnsupportedProtocol, "unsupported privileged protocol"), err(ReasonUnsupportedProtocol, "unsupported privileged protocol")
	}
	if !requestIDPattern.MatchString(req.RequestID) {
		return reject(req.RequestID, req.Operation, "", req.TargetScope, ReasonInvalidRequestID, "invalid privileged request id"), err(ReasonInvalidRequestID, "invalid privileged request id")
	}
	if req.RootNamespace != c.cfg.RootNamespace {
		return reject(req.RequestID, req.Operation, "", req.TargetScope, ReasonRootNamespace, "root namespace mismatch"), err(ReasonRootNamespace, "root namespace mismatch")
	}
	if req.RuntimeIdentity != c.cfg.RuntimeIdentity {
		return reject(req.RequestID, req.Operation, "", req.TargetScope, ReasonRuntimeIdentity, "runtime identity mismatch"), err(ReasonRuntimeIdentity, "runtime identity mismatch")
	}
	spec, ok := c.operationSpecs[req.Operation]
	if !ok {
		return reject(req.RequestID, req.Operation, "", req.TargetScope, ReasonUnknownOperation, "operation is not allowlisted"), err(ReasonUnknownOperation, "operation is not allowlisted")
	}
	if !containsCapability(spec.CapabilityClasses, req.CapabilityClass) {
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonUnauthorized, "capability class not admitted for operation"), err(ReasonUnauthorized, "capability class not admitted for operation")
	}
	if _, ok := c.scopes[req.TargetScope]; !ok {
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonWrongScope, "target scope is not allowlisted"), err(ReasonWrongScope, "target scope is not allowlisted")
	}
	if err := validatePayloadFields(req.Payload, spec.AllowedPayloadFields); err != nil {
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonUnknownPayloadField, err.Error()), err
	}

	c.mu.Lock()
	defer c.mu.Unlock()
	if c.queued >= c.cfg.MaxQueueDepth || c.queued >= c.cfg.MaxWorkers+c.cfg.MaxQueueDepth {
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonQueueExhausted, "privileged queue is full"), err(ReasonQueueExhausted, "privileged queue is full")
	}
	if spec.Class == ClassDestructive && c.runningDestructive {
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonDestructiveSingleRun, "destructive class is globally single-flight"), err(ReasonDestructiveSingleRun, "destructive class is globally single-flight")
	}
	reqHash := hash(raw)
	if existingID, ok := c.requestIndex[req.RequestID]; ok {
		record := c.records[existingID]
		if record.RequestHash == reqHash {
			return Admission{Decision: DecisionAdmitted, OperationID: existingID, RequestID: req.RequestID, Operation: req.Operation, Class: spec.Class, QueueDepth: c.queued, Destructive: spec.Class == ClassDestructive, NoReplayToken: record.NoReplayToken}, nil
		}
		return reject(req.RequestID, req.Operation, spec.Class, req.TargetScope, ReasonUnauthorized, "request id replayed with different payload"), err(ReasonUnauthorized, "request id replayed with different payload")
	}
	operationID := "priv-" + req.RequestID
	now := time.Now().UnixMilli()
	if req.TransportEpoch == 0 {
		req.TransportEpoch = 1
	}
	record := Record{OperationID: operationID, RequestID: req.RequestID, RequestHash: reqHash, Operation: req.Operation, Class: spec.Class, CapabilityClass: req.CapabilityClass, TargetScope: req.TargetScope, Status: StatusQueued, TransportEpoch: req.TransportEpoch, Boot: c.cfg.Boot, NoReplayToken: hash([]byte(c.cfg.Boot.BootID + ":" + c.cfg.Boot.ProcessID + ":" + req.RequestID)), CreatedUnixMs: now, UpdatedUnixMs: now}
	c.records[operationID] = record
	c.requestIndex[req.RequestID] = operationID
	c.queued++
	if spec.Class == ClassDestructive {
		c.runningDestructive = true
	}
	return Admission{Decision: DecisionAdmitted, OperationID: operationID, RequestID: req.RequestID, Operation: req.Operation, Class: spec.Class, QueueDepth: c.queued, Destructive: spec.Class == ClassDestructive, NoReplayToken: record.NoReplayToken}, nil
}

func (c *Controller) Status(operationID string) (Record, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	record, ok := c.records[operationID]
	return record, ok
}

func (c *Controller) Complete(operationID string, status Status) (Record, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	record, ok := c.records[operationID]
	if !ok {
		return Record{}, err(ReasonUnknownOperation, "operation not found")
	}
	if record.Status == StatusCompleted || record.Status == StatusRejected {
		return record, nil
	}
	if status == "" {
		status = StatusCompleted
	}
	record.Status = status
	record.UpdatedUnixMs = time.Now().UnixMilli()
	c.records[operationID] = record
	if c.queued > 0 {
		c.queued--
	}
	if record.Class == ClassDestructive {
		c.runningDestructive = false
	}
	return record, nil
}

func (c *Controller) MarkTransportLost(operationID string) (Record, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	record, ok := c.records[operationID]
	if !ok {
		return Record{}, err(ReasonUnknownOperation, "operation not found")
	}
	record.Status = StatusReconcileRequired
	record.ReconcileRequired = true
	record.Reason = ReasonLostTransport
	record.Message = "transport lost; status/reconcile required before any replay"
	record.UpdatedUnixMs = time.Now().UnixMilli()
	c.records[operationID] = record
	return record, nil
}

func (c *Controller) Reconcile(operationID string, boot BootIdentity) (Record, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	record, ok := c.records[operationID]
	if !ok {
		return Record{}, err(ReasonUnknownOperation, "operation not found")
	}
	if boot.BootID == "" {
		boot = c.cfg.Boot
	}
	if boot.BootID != record.Boot.BootID || boot.ProcessID != record.Boot.ProcessID {
		record.Status = StatusRestartBlocked
		record.ReconcileRequired = true
		record.Reason = ReasonRestartNoReplay
		record.Message = "root runtime identity changed; blind replay is forbidden"
	} else if record.Status == StatusReconcileRequired {
		record.Message = "same root identity observed; caller must lookup durable terminal state"
	}
	record.UpdatedUnixMs = time.Now().UnixMilli()
	c.records[operationID] = record
	return record, nil
}

func (c *Controller) EncodeResponse(value any) ([]byte, error) {
	encoded, encodeErr := json.Marshal(value)
	if encodeErr != nil {
		return nil, encodeErr
	}
	if len(encoded) > c.cfg.MaxResponseBytes {
		return nil, err(ReasonResponseTooLarge, "privileged response exceeds bounded frame size")
	}
	return encoded, nil
}

func (c *Controller) EncodeEvent(value any) ([]byte, error) {
	encoded, encodeErr := json.Marshal(value)
	if encodeErr != nil {
		return nil, encodeErr
	}
	if len(encoded) > c.cfg.MaxEventBytes {
		return nil, err(ReasonEventTooLarge, "privileged event exceeds bounded frame size")
	}
	return encoded, nil
}

func validatePayloadFields(payload json.RawMessage, allowed []string) error {
	if len(payload) == 0 || string(payload) == "null" {
		return nil
	}
	var fields map[string]json.RawMessage
	dec := json.NewDecoder(bytes.NewReader(payload))
	dec.DisallowUnknownFields()
	if decodeErr := dec.Decode(&fields); decodeErr != nil {
		return err(ReasonInvalidJSON, "payload must be a JSON object")
	}
	allowedSet := map[string]struct{}{}
	for _, field := range allowed {
		allowedSet[field] = struct{}{}
	}
	for field := range fields {
		if _, ok := allowedSet[field]; !ok {
			return err(ReasonUnknownPayloadField, "payload field "+field+" is not allowlisted")
		}
	}
	return nil
}

func reject(requestID, operation string, class OperationClass, scope string, reason Reason, message string) Admission {
	return Admission{Decision: DecisionRejected, RequestID: requestID, Operation: operation, Class: class, TargetScope: scope, Reason: reason, Message: message}
}

func containsCapability(classes []CapabilityClass, candidate CapabilityClass) bool {
	for _, class := range classes {
		if class == candidate {
			return true
		}
	}
	return false
}

func hash(raw []byte) string {
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func ReasonOf(err error) Reason {
	if err == nil {
		return ""
	}
	var e Error
	if errors.As(err, &e) {
		return e.Reason
	}
	return Reason(protocol.ReasonOf(err))
}
