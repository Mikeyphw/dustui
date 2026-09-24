package operations

import (
	"encoding/json"
	"sort"
	"sync"

	"github.com/mikeyphw/dustui/go/internal/protocol"
)

type State string

const (
	StateQueued           State = "queued"
	StateRunning          State = "running"
	StateCancelling       State = "cancelling"
	StateSuccess          State = "success"
	StateFailure          State = "failure"
	StateCancelled        State = "cancelled"
	StateInterrupted      State = "interrupted"
	StateUnknown          State = "unknown"
	StateRecoveryRequired State = "recovery_required"
)

func (s State) Terminal() bool {
	switch s {
	case StateSuccess, StateFailure, StateCancelled, StateInterrupted, StateRecoveryRequired:
		return true
	default:
		return false
	}
}

func (s State) Valid() bool {
	switch s {
	case StateQueued, StateRunning, StateCancelling, StateSuccess, StateFailure, StateCancelled, StateInterrupted, StateUnknown, StateRecoveryRequired:
		return true
	default:
		return false
	}
}

type Request struct {
	RequestID  string          `json:"requestId"`
	Payload    json.RawMessage `json:"payload"`
	Generation int64           `json:"generation"`
}

type Event struct {
	OperationID string              `json:"operationId"`
	Sequence    int64               `json:"sequence"`
	Generation  int64               `json:"generation"`
	State       State               `json:"state"`
	Reason      protocol.ReasonCode `json:"reason,omitempty"`
	Message     string              `json:"message,omitempty"`
}

type Operation struct {
	ID               string  `json:"id"`
	RequestID        string  `json:"requestId"`
	Fingerprint      string  `json:"fingerprint"`
	Generation       int64   `json:"generation"`
	State            State   `json:"state"`
	LastSequence     int64   `json:"lastSequence"`
	TerminalSequence int64   `json:"terminalSequence,omitempty"`
	RecoveryRequired bool    `json:"recoveryRequired"`
	Events           []Event `json:"events"`
}

type Store struct {
	mu           sync.Mutex
	operations   map[string]*Operation
	requestIndex map[string]string
	maxEvents    int
}

func NewStore(maxEvents int) *Store {
	if maxEvents <= 0 {
		maxEvents = 256
	}
	return &Store{operations: map[string]*Operation{}, requestIndex: map[string]string{}, maxEvents: maxEvents}
}

func (s *Store) Admit(operationID string, request Request) (Operation, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if operationID == "" || request.RequestID == "" || request.Generation <= 0 || len(request.Payload) == 0 {
		return Operation{}, protocol.NewError(protocol.ReasonInvalidRequest, "missing operation, request, generation, or payload")
	}
	fingerprint := protocol.Fingerprint(request.Payload)
	if existingID, ok := s.requestIndex[request.RequestID]; ok {
		existing := s.operations[existingID]
		if existing.Fingerprint != fingerprint {
			return Operation{}, protocol.NewError(protocol.ReasonDuplicateRequestMismatch, "request id was reused for a different payload")
		}
		return clone(*existing), nil
	}
	operation := &Operation{
		ID:           operationID,
		RequestID:    request.RequestID,
		Fingerprint:  fingerprint,
		Generation:   request.Generation,
		State:        StateQueued,
		LastSequence: 1,
		Events: []Event{{
			OperationID: operationID,
			Sequence:    1,
			Generation:  request.Generation,
			State:       StateQueued,
			Reason:      "admitted",
			Message:     "operation admitted",
		}},
	}
	s.operations[operationID] = operation
	s.requestIndex[request.RequestID] = operationID
	return clone(*operation), nil
}

func (s *Store) Apply(expectedGeneration int64, event Event) (Operation, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	operation := s.operations[event.OperationID]
	if operation == nil {
		return Operation{}, protocol.Recovery(protocol.ReasonCorruptStateRecoveryNeeded, "operation not found; reconciliation required")
	}
	if event.Generation != operation.Generation || expectedGeneration != operation.Generation {
		return clone(*operation), protocol.NewError(protocol.ReasonStaleGeneration, "event generation does not match operation generation")
	}
	if !event.State.Valid() {
		operation.State = StateRecoveryRequired
		operation.RecoveryRequired = true
		operation.LastSequence++
		operation.TerminalSequence = operation.LastSequence
		operation.Events = appendBounded(operation.Events, Event{OperationID: operation.ID, Sequence: operation.LastSequence, Generation: operation.Generation, State: StateRecoveryRequired, Reason: protocol.ReasonCorruptStateRecoveryNeeded, Message: "invalid operation state"}, s.maxEvents)
		return clone(*operation), protocol.Recovery(protocol.ReasonCorruptStateRecoveryNeeded, "invalid operation state; recovery required")
	}
	if event.Sequence <= operation.LastSequence {
		return clone(*operation), protocol.NewError(protocol.ReasonLateEvent, "event sequence is not newer than the durable operation sequence")
	}
	if operation.State.Terminal() {
		return clone(*operation), protocol.NewError(protocol.ReasonTerminalStateSticky, "terminal operation state cannot be changed by a later event")
	}
	if operation.State == StateCancelling && event.State != StateCancelled && event.State != StateRecoveryRequired {
		return clone(*operation), protocol.NewError(protocol.ReasonCancellationSticky, "cancellation intent cannot move back to a non-cancel terminal path")
	}
	operation.State = event.State
	operation.LastSequence = event.Sequence
	if event.State.Terminal() {
		operation.TerminalSequence = event.Sequence
	}
	if event.State == StateRecoveryRequired {
		operation.RecoveryRequired = true
	}
	operation.Events = appendBounded(operation.Events, event, s.maxEvents)
	return clone(*operation), nil
}

func (s *Store) Operation(id string) (Operation, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	op := s.operations[id]
	if op == nil {
		return Operation{}, false
	}
	return clone(*op), true
}

func appendBounded(events []Event, event Event, limit int) []Event {
	events = append(events, event)
	if len(events) <= limit {
		return events
	}
	copy(events, events[len(events)-limit:])
	return events[:limit]
}

func clone(operation Operation) Operation {
	operation.Events = append([]Event(nil), operation.Events...)
	sort.Slice(operation.Events, func(i, j int) bool { return operation.Events[i].Sequence < operation.Events[j].Sequence })
	return operation
}
