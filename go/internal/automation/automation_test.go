package automation

import (
	"testing"
	"time"

	"github.com/mikeyphw/dustui/go/internal/operations"
	"github.com/mikeyphw/dustui/go/internal/protocol"
)

func TestAdaptersObserveSameDurableOperationAndTerminalTruth(t *testing.T) {
	controller := NewController()
	ui, err := controller.Admit(AdmissionRequest{Source: AdapterUI, RequestID: "req-scan", Kind: OperationScan, Generation: 42, Payload: map[string]string{"root": "/sdcard"}})
	if err != nil {
		t.Fatal(err)
	}
	if !ui.Accepted || ui.Success || ui.Terminal || !ui.Queued {
		t.Fatalf("accepted/queued must not masquerade as success: %+v", ui)
	}
	wm, err := controller.Admit(AdmissionRequest{Source: AdapterWorkManager, RequestID: "req-scan", Kind: OperationScan, Generation: 42, Payload: map[string]string{"root": "/sdcard"}})
	if err != nil {
		t.Fatal(err)
	}
	tasker, err := controller.Admit(AdmissionRequest{Source: AdapterTasker, RequestID: "req-scan", Kind: OperationScan, Generation: 42, Payload: map[string]string{"root": "/sdcard"}})
	if err != nil {
		t.Fatal(err)
	}
	if ui.OperationID != wm.OperationID || wm.OperationID != tasker.OperationID {
		t.Fatalf("thin adapters must observe the same durable operation: ui=%s wm=%s tasker=%s", ui.OperationID, wm.OperationID, tasker.OperationID)
	}
	if _, err := controller.Report(ui.OperationID, operations.StateRunning, "running", "scan started"); err != nil {
		t.Fatal(err)
	}
	running, err := controller.Observe(ui.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if running.Terminal || running.TerminalTruth != TerminalPending {
		t.Fatalf("running is not terminal success: %+v", running)
	}
	if _, err := controller.Report(ui.OperationID, operations.StateSuccess, "complete", "scan complete"); err != nil {
		t.Fatal(err)
	}
	terminal, err := controller.Observe(ui.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if !terminal.Terminal || terminal.TerminalTruth != TerminalSuccess {
		t.Fatalf("terminal truth not observed equivalently: %+v", terminal)
	}
	history := controller.Activity(ActivityHistory)
	if len(history) != 1 || history[0].OperationID != ui.OperationID || history[0].TerminalTruth != TerminalSuccess {
		t.Fatalf("activity history missing terminal operation: %+v", history)
	}
}

func TestNotificationNonceExpiryReplayAndOperationBinding(t *testing.T) {
	controller := NewController()
	now := time.Unix(100, 0)
	admitted, err := controller.Admit(AdmissionRequest{Source: AdapterUI, RequestID: "req-status", Kind: OperationStatus, Generation: 1})
	if err != nil {
		t.Fatal(err)
	}
	action, err := controller.NotificationAction(admitted.OperationID, string(OperationCancel), now, time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	cancelled, err := controller.Admit(AdmissionRequest{Source: AdapterNotification, RequestID: "req-cancel", Kind: OperationCancel, Generation: 1, TargetOperationID: admitted.OperationID, NotificationNonce: action.Nonce, Now: now.Add(30 * time.Second)})
	if err != nil {
		t.Fatal(err)
	}
	if cancelled.OperationID != admitted.OperationID || cancelled.State != operations.StateCancelling || cancelled.Success {
		t.Fatalf("notification cancel must be an operation transition, not success: %+v", cancelled)
	}
	_, replay := controller.Admit(AdmissionRequest{Source: AdapterNotification, RequestID: "req-cancel-replay", Kind: OperationCancel, Generation: 1, TargetOperationID: admitted.OperationID, NotificationNonce: action.Nonce, Now: now.Add(31 * time.Second)})
	if replay == nil || protocol.ReasonOf(replay) != protocol.ReasonStateConflict {
		t.Fatalf("replayed notification nonce should be rejected, got %v", replay)
	}
	expired, err := controller.NotificationAction(admitted.OperationID, string(OperationCancel), now, time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	_, expiredErr := controller.Admit(AdmissionRequest{Source: AdapterNotification, RequestID: "req-expired", Kind: OperationCancel, Generation: 1, TargetOperationID: admitted.OperationID, NotificationNonce: expired.Nonce, Now: now.Add(2 * time.Minute)})
	if expiredErr == nil || protocol.ReasonOf(expiredErr) != protocol.ReasonInvalidRequest {
		t.Fatalf("expired notification action should fail closed, got %v", expiredErr)
	}
}

func TestProcessDeathDoesNotOrphanNonTerminalOperation(t *testing.T) {
	controller := NewController()
	admitted, err := controller.Admit(AdmissionRequest{Source: AdapterWorkManager, RequestID: "req-long", Kind: OperationScan, Generation: 7})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := controller.Report(admitted.OperationID, operations.StateRunning, "running", "active scan"); err != nil {
		t.Fatal(err)
	}
	plan := controller.ReconcileAfterProcessDeath(admitted.OperationID)
	if !plan.Required || plan.BlindReplayAllowed || plan.Reason != "non_terminal_operation_survived_process_death" {
		t.Fatalf("process death must require reconcile without blind replay: %+v", plan)
	}
	if _, err := controller.Report(admitted.OperationID, operations.StateRecoveryRequired, protocol.ReasonCorruptStateRecoveryNeeded, "lost transport requires recovery"); err != nil {
		t.Fatal(err)
	}
	recovery := controller.Activity(ActivityRecovery)
	if len(recovery) != 1 || !recovery[0].CanReconcile || !recovery[0].RecoveryRequired {
		t.Fatalf("activity recovery view should expose reconcile gate: %+v", recovery)
	}
}

func TestAutomationRejectsDestructiveUnattendedOperations(t *testing.T) {
	controller := NewController()
	_, err := controller.Admit(AdmissionRequest{Source: AdapterTasker, RequestID: "req-delete", Kind: OperationKind("cleanup.execute"), Generation: 1})
	if err == nil || protocol.ReasonOf(err) != protocol.ReasonInvalidRequest {
		t.Fatalf("unattended destructive automation must be rejected, got %v", err)
	}
}
