package state

import (
	"context"
	"encoding/json"
	"path/filepath"
	"testing"
)

func openAUD_FIX11Store(t *testing.T) *SQLiteStore {
	t.Helper()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	return store
}

func TestAUD_FIX11CleanupVerificationIsActivityEvidence(t *testing.T) {
	ctx := context.Background()
	store := openAUD_FIX11Store(t)
	done := int64(2000)
	op := AndroidCleanupOperation{ID: "cleanup-1", ScanID: "scan-1", RootPath: "/data", Status: "COMPLETED", SelectedCount: 1, PlannedBytes: 4096, DeletedBytes: 4096, CreatedAt: 1000, CompletedAt: &done, ReviewID: "manifest-1", ScanGeneration: 7, PlanHash: "plan-1", OutcomeCertainty: "CERTAIN"}
	if _, err := store.InsertAndroidCleanupOperationIfAbsent(ctx, op); err != nil {
		t.Fatal(err)
	}
	report := VerificationReport{SchemaVersion: 2, OperationID: op.ID, ScanID: op.ScanID, RootPath: op.RootPath, StartedAt: 1000, CompletedAt: 2000, PlannedBytes: 4096, AttemptedBytes: 4096, ReportedDeletedBytes: 4096, ConfirmedDeletedBytes: 4096, VerificationAttempts: 2, LastVerifiedAt: 2100}
	if err := store.PersistVerificationReport(ctx, report, 7); err != nil {
		t.Fatal(err)
	}
	evidence, err := store.ActivityEvidenceByID(ctx, op.ID)
	if err != nil {
		t.Fatal(err)
	}
	if evidence.CleanupVerification == nil || evidence.CleanupVerification.ConfirmedDeletedBytes != 4096 || evidence.VerificationGeneration != 7 {
		t.Fatalf("cleanup verification not folded into Activity: %#v", evidence)
	}
	var raw VerificationReport
	if err := json.Unmarshal(evidence.Run.Verification, &raw); err != nil {
		t.Fatal(err)
	}
	if raw.OperationID != op.ID || raw.VerificationAttempts != 2 {
		t.Fatalf("activity verification envelope is not the full durable report: %#v", raw)
	}
}

func TestAUD_FIX11DiagnosticsRemediationTargetsExactRecoveryOperation(t *testing.T) {
	ctx := context.Background()
	store := openAUD_FIX11Store(t)
	admission, err := store.AdmitAutomation(ctx, "scan-r1", "ui", "scan", 9, "scan-r1")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, admission.OperationID, "recovery_required", "transport_lost", "Reconcile interrupted work"); err != nil {
		t.Fatal(err)
	}
	snapshot, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, problem := range snapshot.Problems {
		if problem.OperationID != admission.OperationID {
			continue
		}
		found = true
		if len(problem.RemediationActions) == 0 || problem.RemediationActions[0].Route != "activity/run" || problem.RemediationActions[0].EntityID != admission.OperationID {
			t.Fatalf("diagnostics does not navigate to exact correlated run: %#v", problem)
		}
	}
	if !found {
		t.Fatalf("expected operation-correlated diagnostic problem: %#v", snapshot.Problems)
	}
}
