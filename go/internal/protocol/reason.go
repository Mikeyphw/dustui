package protocol

import "fmt"

type ReasonCode string

const (
	ReasonInvalidRequest             ReasonCode = "invalid_request"
	ReasonDuplicateRequestMismatch   ReasonCode = "duplicate_request_mismatch"
	ReasonStaleGeneration            ReasonCode = "stale_generation"
	ReasonLateEvent                  ReasonCode = "late_event"
	ReasonTerminalStateSticky        ReasonCode = "terminal_state_sticky"
	ReasonCancellationSticky         ReasonCode = "cancellation_sticky"
	ReasonCorruptStateRecoveryNeeded ReasonCode = "corrupt_state_recovery_required"
	ReasonStateConflict              ReasonCode = "state_conflict"
	ReasonSchemaMigrationFailed      ReasonCode = "schema_migration_failed"
	ReasonUnsupportedEnvelope        ReasonCode = "unsupported_envelope"
)

type Error struct {
	Reason           ReasonCode `json:"reason"`
	Message          string     `json:"message"`
	RecoveryRequired bool       `json:"recoveryRequired"`
}

func (e Error) Error() string {
	if e.Message == "" {
		return string(e.Reason)
	}
	return fmt.Sprintf("%s: %s", e.Reason, e.Message)
}

func NewError(reason ReasonCode, message string) Error {
	return Error{Reason: reason, Message: message, RecoveryRequired: reason == ReasonCorruptStateRecoveryNeeded}
}

func Recovery(reason ReasonCode, message string) Error {
	return Error{Reason: reason, Message: message, RecoveryRequired: true}
}

func ReasonOf(err error) ReasonCode {
	if err == nil {
		return ""
	}
	if typed, ok := err.(Error); ok {
		return typed.Reason
	}
	if typed, ok := err.(*Error); ok {
		return typed.Reason
	}
	return ReasonStateConflict
}
