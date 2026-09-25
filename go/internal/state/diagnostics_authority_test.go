package state

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/mikeyphw/dustui/go/internal/diagnostics"
)

func openAUD_FIX05Store(t *testing.T) *SQLiteStore {
	t.Helper()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	return store
}

func TestAUD_FIX05DiagnosticsAreRedactedBoundedAndDurable(t *testing.T) {
	store := openAUD_FIX05Store(t)
	ctx := context.Background()
	policy := diagnostics.DefaultDiagnosticPolicy()
	old := time.Now().AddDate(0, 0, -policy.DiagnosticRetainDays-1).UnixMilli()
	_, err := store.RecordDiagnostic(ctx, diagnostics.DiagnosticEvent{SessionID: "old", Area: "scan", Severity: diagnostics.SeverityWarning, Action: "old", Result: "x", Values: map[string]string{"rawPath": "/data/secret"}, CreatedAtMS: old})
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < policy.MaxEventsPerSession+5; i++ {
		_, err = store.RecordDiagnostic(ctx, diagnostics.DiagnosticEvent{SessionID: "live", Area: "scan", Severity: diagnostics.SeverityInfo, Action: fmt.Sprintf("e-%d", i), Result: "ok", Values: map[string]string{"rawPath": "/data/private"}, CreatedAtMS: time.Now().UnixMilli() + int64(i)})
		if err != nil {
			t.Fatal(err)
		}
	}
	events, err := store.DiagnosticEvents(ctx, policy.MaxEventsPerSession+100)
	if err != nil {
		t.Fatal(err)
	}
	if len(events) > policy.MaxEventsPerSession {
		t.Fatalf("unbounded diagnostic events: %d", len(events))
	}
	for _, e := range events {
		if e.Values["rawPath"] == "/data/private" {
			t.Fatalf("raw path persisted: %#v", e)
		}
	}
	snap, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if snap.Protocol != diagnostics.Protocol || len(snap.EvidenceGenerations) < 3 || snap.Retention.MaxSupportBundles <= 0 {
		t.Fatalf("incomplete diagnostic snapshot: %#v", snap)
	}
}

func TestAUD_FIX05SupportBundleUsesDurableOperationAndCanBeDeleted(t *testing.T) {
	store := openAUD_FIX05Store(t)
	ctx := context.Background()
	snap, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	admission, err := store.AdmitAutomation(ctx, "support-1", "ui", "support-export", 1, "fixture")
	if err != nil {
		t.Fatal(err)
	}
	if _, err = store.AdvanceAutomation(ctx, admission.OperationID, "running", "fixture", "fixture"); err != nil {
		t.Fatal(err)
	}
	bundle := diagnostics.NewSupportBundle(diagnostics.PrivacyMinimal, snap.EvidenceGenerations, snap.Problems, nil)
	bundle.OperationID = admission.OperationID
	if err = store.PersistSupportBundle(ctx, admission.OperationID, bundle); err != nil {
		t.Fatal(err)
	}
	terminal, err := store.AdvanceAutomation(ctx, admission.OperationID, "success", "sealed", "sealed")
	if err != nil {
		t.Fatal(err)
	}
	if terminal.TerminalTruth != "success" {
		t.Fatalf("support export terminal truth=%s", terminal.TerminalTruth)
	}
	if err = store.DeleteSupportBundle(ctx, admission.OperationID); err != nil {
		t.Fatal(err)
	}
}

func TestAUD_FIX05ProblemGraphCorrelatesRecoveryOperationAndPersistsSignificantRecord(t *testing.T) {
	store := openAUD_FIX05Store(t)
	ctx := context.Background()
	admission, err := store.AdmitAutomation(ctx, "recovery-request", "ui", "scan", 1, "scan|recovery")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, admission.OperationID, "recovery_required", "process_death", "operation needs reconciliation"); err != nil {
		t.Fatal(err)
	}
	snapshot, err := store.DiagnosticsSnapshot(ctx)
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, p := range snapshot.Problems {
		if strings.Contains(p.ID, "operations") {
			found = true
			if p.OperationID != admission.OperationID {
				t.Fatalf("problem must correlate durable operation: %#v", p)
			}
		}
	}
	if !found {
		t.Fatalf("expected operations problem: %#v", snapshot.Problems)
	}
	var count int
	if err := store.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM significant_problem_records WHERE operation_id=?`, admission.OperationID).Scan(&count); err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("expected one durable significant problem record, got %d", count)
	}
}
