package state

import (
	"context"

	"github.com/mikeyphw/dustui/go/internal/diagnostics"
)

// ActivityEvidence is the product-facing evidence envelope for one durable run.
// Cleanup verification is folded into Activity rather than exposed as a peer product.
type ActivityEvidence struct {
	Protocol               string                `json:"protocol"`
	OperationID            string                `json:"operationId"`
	EvidenceCorrelation    string                `json:"evidenceCorrelation"`
	Run                    ActivityRun           `json:"run"`
	CleanupVerification    *VerificationReport   `json:"cleanupVerification,omitempty"`
	VerificationGeneration int64                 `json:"verificationGeneration,omitempty"`
	Problems               []diagnostics.Problem `json:"problems"`
}

func (s *SQLiteStore) ActivityEvidenceByID(ctx context.Context, operationID string) (ActivityEvidence, error) {
	run, err := s.ActivityRunByID(ctx, operationID)
	if err != nil {
		return ActivityEvidence{}, err
	}
	report, generation, err := s.VerificationReport(ctx, operationID)
	if err != nil {
		return ActivityEvidence{}, err
	}
	snapshot, err := s.DiagnosticsSnapshot(ctx)
	if err != nil {
		return ActivityEvidence{}, err
	}
	problems := make([]diagnostics.Problem, 0)
	for _, problem := range snapshot.Problems {
		if problem.OperationID == operationID {
			problems = append(problems, problem)
		}
	}
	return ActivityEvidence{
		Protocol:               AutomationProtocol,
		OperationID:            operationID,
		EvidenceCorrelation:    run.EvidenceCorrelation,
		Run:                    run,
		CleanupVerification:    report,
		VerificationGeneration: generation,
		Problems:               problems,
	}, nil
}
