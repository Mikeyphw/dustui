package operations

import (
	"encoding/json"
	"testing"

	"github.com/mikeyphw/dustui/go/internal/protocol"
)

func payload(t *testing.T, v any) json.RawMessage {
	t.Helper()
	raw, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	return raw
}

func TestDuplicateRequestIDCannotChangePayload(t *testing.T) {
	store := NewStore(16)
	_, err := store.Admit("op-1", Request{RequestID: "req-1", Payload: payload(t, map[string]string{"path": "/a"}), Generation: 1})
	if err != nil {
		t.Fatal(err)
	}
	_, err = store.Admit("op-2", Request{RequestID: "req-1", Payload: payload(t, map[string]string{"path": "/b"}), Generation: 1})
	if protocol.ReasonOf(err) != protocol.ReasonDuplicateRequestMismatch {
		t.Fatalf("reason = %q err=%v", protocol.ReasonOf(err), err)
	}
}

func TestTerminalStateIsStickyAndRejectsLateResurrection(t *testing.T) {
	store := NewStore(16)
	_, err := store.Admit("op-1", Request{RequestID: "req-1", Payload: payload(t, "payload"), Generation: 7})
	if err != nil {
		t.Fatal(err)
	}
	if _, err = store.Apply(7, Event{OperationID: "op-1", Sequence: 2, Generation: 7, State: StateSuccess}); err != nil {
		t.Fatal(err)
	}
	_, err = store.Apply(7, Event{OperationID: "op-1", Sequence: 3, Generation: 7, State: StateRunning})
	if protocol.ReasonOf(err) != protocol.ReasonTerminalStateSticky {
		t.Fatalf("terminal resurrection reason = %q err=%v", protocol.ReasonOf(err), err)
	}
	_, err = store.Apply(7, Event{OperationID: "op-1", Sequence: 2, Generation: 7, State: StateFailure})
	if protocol.ReasonOf(err) != protocol.ReasonLateEvent {
		t.Fatalf("late event reason = %q err=%v", protocol.ReasonOf(err), err)
	}
}

func TestCancellationIsSticky(t *testing.T) {
	store := NewStore(16)
	_, err := store.Admit("op-1", Request{RequestID: "req-1", Payload: payload(t, "payload"), Generation: 1})
	if err != nil {
		t.Fatal(err)
	}
	if _, err = store.Apply(1, Event{OperationID: "op-1", Sequence: 2, Generation: 1, State: StateRunning}); err != nil {
		t.Fatal(err)
	}
	if _, err = store.Apply(1, Event{OperationID: "op-1", Sequence: 3, Generation: 1, State: StateCancelling}); err != nil {
		t.Fatal(err)
	}
	_, err = store.Apply(1, Event{OperationID: "op-1", Sequence: 4, Generation: 1, State: StateSuccess})
	if protocol.ReasonOf(err) != protocol.ReasonCancellationSticky {
		t.Fatalf("cancellation reason = %q err=%v", protocol.ReasonOf(err), err)
	}
	if _, err = store.Apply(1, Event{OperationID: "op-1", Sequence: 5, Generation: 1, State: StateCancelled}); err != nil {
		t.Fatal(err)
	}
}

func TestExpectedGenerationRejectsStaleActors(t *testing.T) {
	store := NewStore(16)
	_, err := store.Admit("op-1", Request{RequestID: "req-1", Payload: payload(t, "payload"), Generation: 9})
	if err != nil {
		t.Fatal(err)
	}
	_, err = store.Apply(8, Event{OperationID: "op-1", Sequence: 2, Generation: 8, State: StateRunning})
	if protocol.ReasonOf(err) != protocol.ReasonStaleGeneration {
		t.Fatalf("stale reason = %q err=%v", protocol.ReasonOf(err), err)
	}
}

func TestInvalidStateBecomesRecoveryEvidence(t *testing.T) {
	store := NewStore(16)
	_, err := store.Admit("op-1", Request{RequestID: "req-1", Payload: payload(t, "payload"), Generation: 1})
	if err != nil {
		t.Fatal(err)
	}
	op, err := store.Apply(1, Event{OperationID: "op-1", Sequence: 2, Generation: 1, State: State("done-maybe")})
	if protocol.ReasonOf(err) != protocol.ReasonCorruptStateRecoveryNeeded {
		t.Fatalf("recovery reason = %q err=%v", protocol.ReasonOf(err), err)
	}
	if op.State != StateRecoveryRequired || !op.RecoveryRequired {
		t.Fatalf("expected recovery evidence, got %+v", op)
	}
}
