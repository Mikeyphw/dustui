package protocol

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"time"

	"github.com/mikeyphw/dustui/go/internal/version"
)

type EnvelopeType string

const (
	EnvelopeReadiness   EnvelopeType = "readiness"
	EnvelopeExecute     EnvelopeType = "execute"
	EnvelopeAdmit       EnvelopeType = "admit"
	EnvelopeStatus      EnvelopeType = "status"
	EnvelopeProgress    EnvelopeType = "progress"
	EnvelopeCancel      EnvelopeType = "cancel"
	EnvelopeReconcile   EnvelopeType = "reconcile"
	EnvelopeQueryPage   EnvelopeType = "query_page"
	EnvelopeDiagnostics EnvelopeType = "diagnostics"
	EnvelopeEvidence    EnvelopeType = "evidence"
)

type Envelope struct {
	Protocol      string          `json:"protocol"`
	Type          EnvelopeType    `json:"type"`
	RequestID     string          `json:"requestId"`
	OperationID   string          `json:"operationId,omitempty"`
	Sequence      int64           `json:"sequence"`
	Generation    int64           `json:"generation"`
	Fingerprint   string          `json:"fingerprint"`
	Payload       json.RawMessage `json:"payload,omitempty"`
	CreatedUnixMs int64           `json:"createdUnixMs"`
}

func NewEnvelope(kind EnvelopeType, requestID string, generation int64, payload any) (Envelope, error) {
	encoded, err := json.Marshal(payload)
	if err != nil {
		return Envelope{}, NewError(ReasonInvalidRequest, err.Error())
	}
	return Envelope{
		Protocol:      version.Protocol,
		Type:          kind,
		RequestID:     requestID,
		Sequence:      1,
		Generation:    generation,
		Fingerprint:   Fingerprint(encoded),
		Payload:       encoded,
		CreatedUnixMs: time.Now().UnixMilli(),
	}, nil
}

func (e Envelope) Validate() error {
	if e.Protocol != version.Protocol {
		return NewError(ReasonUnsupportedEnvelope, "unsupported protocol "+e.Protocol)
	}
	if e.Type == "" || e.RequestID == "" || e.Sequence <= 0 || e.Generation <= 0 {
		return NewError(ReasonInvalidRequest, "envelope is missing required identity fields")
	}
	if e.Fingerprint == "" || e.Fingerprint != Fingerprint(e.Payload) {
		return NewError(ReasonInvalidRequest, "payload fingerprint mismatch")
	}
	return nil
}

func Fingerprint(payload []byte) string {
	sum := sha256.Sum256(payload)
	return hex.EncodeToString(sum[:])
}

func SupportedEnvelopeTypes() []EnvelopeType {
	return []EnvelopeType{
		EnvelopeReadiness,
		EnvelopeExecute,
		EnvelopeAdmit,
		EnvelopeStatus,
		EnvelopeProgress,
		EnvelopeCancel,
		EnvelopeReconcile,
		EnvelopeQueryPage,
		EnvelopeDiagnostics,
		EnvelopeEvidence,
	}
}
