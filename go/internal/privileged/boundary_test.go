package privileged

import (
	"encoding/json"
	"strings"
	"testing"
)

func request(t *testing.T, mutate func(map[string]any)) []byte {
	t.Helper()
	m := map[string]any{
		"protocol":        ProtocolName,
		"requestId":       "req-123",
		"operation":       "root.readiness",
		"capabilityClass": string(CapabilityPrivilegedRead),
		"targetScope":     "app-owned-runtime",
		"rootNamespace":   DefaultRootNamespace,
		"runtimeIdentity": DefaultRuntimeIdentity,
		"payload":         map[string]any{"includeDiagnostics": true},
	}
	if mutate != nil {
		mutate(m)
	}
	encoded, err := json.Marshal(m)
	if err != nil {
		t.Fatal(err)
	}
	return encoded
}

func controller(t *testing.T) *Controller {
	t.Helper()
	c, err := NewController(DefaultConfig())
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func TestPrivilegedAdmissionRejectsUnknownsAndBounds(t *testing.T) {
	c := controller(t)
	if _, err := c.Admit(request(t, func(m map[string]any) { m["protocol"] = "old" })); ReasonOf(err) != ReasonUnsupportedProtocol {
		t.Fatalf("unsupported protocol reason = %v", err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) { m["operation"] = "root.shell" })); ReasonOf(err) != ReasonUnknownOperation {
		t.Fatalf("unknown operation reason = %v", err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) { m["targetScope"] = "/" })); ReasonOf(err) != ReasonWrongScope {
		t.Fatalf("wrong scope reason = %v", err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) { m["payload"] = map[string]any{"unexpected": true} })); ReasonOf(err) != ReasonUnknownPayloadField {
		t.Fatalf("unknown field reason = %v", err)
	}
	if _, err := c.Admit([]byte(strings.Repeat("x", DefaultMaxPayloadBytes+1))); ReasonOf(err) != ReasonPayloadTooLarge {
		t.Fatalf("oversized reason = %v", err)
	}
}

func TestPrivilegedAdmissionRequiresCapabilityNamespaceAndRuntimeIdentity(t *testing.T) {
	c := controller(t)
	if _, err := c.Admit(request(t, func(m map[string]any) {
		m["operation"] = "cleanup.execute.dry_run"
		m["capabilityClass"] = string(CapabilityReadOnly)
		m["targetScope"] = "cleanup-dry-run"
		m["payload"] = map[string]any{"manifestId": "m0", "generation": 1, "scope": "cleanup-dry-run", "dryRun": true}
	})); ReasonOf(err) != ReasonUnauthorized {
		t.Fatalf("capability reason = %v", err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) { m["rootNamespace"] = "other" })); ReasonOf(err) != ReasonRootNamespace {
		t.Fatalf("namespace reason = %v", err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) { m["runtimeIdentity"] = "other" })); ReasonOf(err) != ReasonRuntimeIdentity {
		t.Fatalf("runtime reason = %v", err)
	}
}

func TestPrivilegedQueueBoundsAndDestructiveSingleFlight(t *testing.T) {
	cfg := DefaultConfig()
	cfg.MaxQueueDepth = 1
	c, err := NewController(cfg)
	if err != nil {
		t.Fatal(err)
	}
	first, err := c.Admit(request(t, func(m map[string]any) {
		m["requestId"] = "dry-run-1"
		m["operation"] = "cleanup.execute.dry_run"
		m["capabilityClass"] = string(CapabilityDryRunMutation)
		m["targetScope"] = "cleanup-dry-run"
		m["payload"] = map[string]any{"manifestId": "m1", "generation": 1, "scope": "cleanup-dry-run", "dryRun": true}
	}))
	if err != nil || !first.Destructive {
		t.Fatalf("admit destructive dry run: %+v %v", first, err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) {
		m["requestId"] = "dry-run-2"
		m["operation"] = "cleanup.execute.dry_run"
		m["capabilityClass"] = string(CapabilityDryRunMutation)
		m["targetScope"] = "cleanup-dry-run"
		m["payload"] = map[string]any{"manifestId": "m2", "generation": 1, "scope": "cleanup-dry-run", "dryRun": true}
	})); ReasonOf(err) != ReasonQueueExhausted {
		t.Fatalf("queue should fail closed before expansion, got %v", err)
	}
	if _, err := c.Complete(first.OperationID, StatusCompleted); err != nil {
		t.Fatal(err)
	}

	cfg.MaxQueueDepth = 4
	c, _ = NewController(cfg)
	first, err = c.Admit(request(t, func(m map[string]any) {
		m["requestId"] = "dry-run-3"
		m["operation"] = "cleanup.execute.dry_run"
		m["capabilityClass"] = string(CapabilityDryRunMutation)
		m["targetScope"] = "cleanup-dry-run"
		m["payload"] = map[string]any{"manifestId": "m3", "generation": 1, "scope": "cleanup-dry-run", "dryRun": true}
	}))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := c.Admit(request(t, func(m map[string]any) {
		m["requestId"] = "dry-run-4"
		m["operation"] = "cleanup.execute.dry_run"
		m["capabilityClass"] = string(CapabilityDryRunMutation)
		m["targetScope"] = "cleanup-dry-run"
		m["payload"] = map[string]any{"manifestId": "m4", "generation": 1, "scope": "cleanup-dry-run", "dryRun": true}
	})); ReasonOf(err) != ReasonDestructiveSingleRun {
		t.Fatalf("destructive single flight reason = %v", err)
	}
	_, _ = c.Complete(first.OperationID, StatusCompleted)
}

func TestPrivilegedDurableReconcileAndNoReplayAfterRestart(t *testing.T) {
	c := controller(t)
	admission, err := c.Admit(request(t, nil))
	if err != nil {
		t.Fatal(err)
	}
	record, err := c.MarkTransportLost(admission.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if record.Status != StatusReconcileRequired || !record.ReconcileRequired {
		t.Fatalf("lost transport must force reconcile: %+v", record)
	}
	same, err := c.Reconcile(admission.OperationID, record.Boot)
	if err != nil {
		t.Fatal(err)
	}
	if same.Status != StatusReconcileRequired || same.Reason != ReasonLostTransport {
		t.Fatalf("same boot should not replay or invent completion: %+v", same)
	}
	changed, err := c.Reconcile(admission.OperationID, BootIdentity{BootID: "new-boot", ProcessID: "new-pid", StartedMs: record.Boot.StartedMs + 1})
	if err != nil {
		t.Fatal(err)
	}
	if changed.Status != StatusRestartBlocked || changed.Reason != ReasonRestartNoReplay {
		t.Fatalf("restart must block replay: %+v", changed)
	}
}

func TestResponseAndEventFramesAreBounded(t *testing.T) {
	cfg := DefaultConfig()
	cfg.MaxResponseBytes = 8
	cfg.MaxEventBytes = 8
	c, err := NewController(cfg)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := c.EncodeResponse(map[string]string{"payload": "too long"}); ReasonOf(err) != ReasonResponseTooLarge {
		t.Fatalf("response bound reason = %v", err)
	}
	if _, err := c.EncodeEvent(map[string]string{"payload": "too long"}); ReasonOf(err) != ReasonEventTooLarge {
		t.Fatalf("event bound reason = %v", err)
	}
}

func TestOV08ProductionCleanupMutationIsAllowlistedAndBounded(t *testing.T) {
	c := controller(t)
	admission, err := c.Admit(request(t, func(m map[string]any) {
		m["requestId"] = "prod-mut-1"
		m["operation"] = "cleanup.execute"
		m["capabilityClass"] = string(CapabilityDestructive)
		m["targetScope"] = "cleanup-production"
		m["payload"] = map[string]any{"manifestId": "m1", "manifestHash": "hash", "capabilityId": "cap", "operationId": "op", "generation": 8, "scope": "cleanup-production"}
	}))
	if err != nil || !admission.Destructive || admission.Operation != "cleanup.execute" {
		t.Fatalf("production mutation admission failed: %+v %v", admission, err)
	}
	diag := c.Diagnostics()
	if !diag.ProductionMutation || diag.GenericShell {
		t.Fatalf("diagnostics must expose production mutation without shell: %+v", diag)
	}
}
