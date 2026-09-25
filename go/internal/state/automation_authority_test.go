package state

import (
	"context"
	"path/filepath"
	"strings"
	"testing"
)

func TestAUD_FIX04DurableOperationSurvivesRestartAndSharesAdapterIdentity(t *testing.T) {
	ctx := context.Background()
	path := filepath.Join(t.TempDir(), "dustui.db")
	store, err := OpenSQLiteStore(path)
	if err != nil {
		t.Fatal(err)
	}
	ui, err := store.AdmitAutomation(ctx, "scan-42", "ui", "scan", 42, "fp-42")
	if err != nil {
		t.Fatal(err)
	}
	wm, err := store.AdmitAutomation(ctx, "scan-42", "workmanager", "scan", 42, "fp-42")
	if err != nil {
		t.Fatal(err)
	}
	if ui.OperationID != wm.OperationID {
		t.Fatalf("adapters diverged: %s != %s", ui.OperationID, wm.OperationID)
	}
	running, err := store.AdvanceAutomation(ctx, ui.OperationID, "running", "started", "scan running")
	if err != nil {
		t.Fatal(err)
	}
	if running.TerminalTruth != "pending" {
		t.Fatalf("running reported terminal: %+v", running)
	}
	completedAt := int64(12345)
	if err := store.UpsertAndroidScanSession(ctx, AndroidScanSession{ID: "scan-42", RootPath: "/storage/emulated/0", Status: "COMPLETED", Backend: "root", StartedAt: 1, CompletedAt: &completedAt, Generation: 42, IntegrityStatus: "COMPLETE"}); err != nil {
		t.Fatal(err)
	}
	if err := store.Close(); err != nil {
		t.Fatal(err)
	}

	store, err = OpenSQLiteStore(path)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	restored, err := store.ObserveAutomation(ctx, ui.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if restored.State != "running" || restored.LastSequence < 2 {
		t.Fatalf("durable state lost across restart: %+v", restored)
	}
	if len(restored.Sources) != 2 {
		t.Fatalf("adapter sources not durable: %+v", restored.Sources)
	}
	reconciled, err := store.ReconcileAutomation(ctx, ui.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if !reconciled.Allowed || reconciled.State != "success" {
		t.Fatalf("reconnect did not recover terminal scan truth: %+v", reconciled)
	}
	terminal, err := store.ObserveAutomation(ctx, ui.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if terminal.TerminalTruth != "success" {
		t.Fatalf("terminal truth missing after reconcile: %+v", terminal)
	}
	if _, err := store.AdvanceAutomation(ctx, ui.OperationID, "failure", "late", "must be sticky"); err == nil {
		t.Fatal("terminal state accepted late mutation")
	}
}

func TestAUD_FIX04NotificationCapabilityIsBoundSingleUseAndExpiring(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	admission, err := store.AdmitAutomation(ctx, "scan-n", "ui", "scan", 1, "fp-n")
	if err != nil {
		t.Fatal(err)
	}
	capability, err := store.CreateNotificationCapability(ctx, admission.OperationID, "cancel", 60_000)
	if err != nil {
		t.Fatal(err)
	}
	claim, err := store.ClaimNotificationCapability(ctx, capability.Nonce, capability.OperationID, capability.Action, capability.OperationBinding, capability.ExpiresAtMS-1)
	if err != nil {
		t.Fatal(err)
	}
	if !claim.Accepted || claim.RequestID != "scan-n" {
		t.Fatalf("valid capability rejected: %+v", claim)
	}
	replay, err := store.ClaimNotificationCapability(ctx, capability.Nonce, capability.OperationID, capability.Action, capability.OperationBinding, capability.ExpiresAtMS-1)
	if err != nil {
		t.Fatal(err)
	}
	if replay.Accepted || replay.Reason != "replay" {
		t.Fatalf("replay accepted: %+v", replay)
	}

	expiring, err := store.CreateNotificationCapability(ctx, admission.OperationID, "cancel", 60_000)
	if err != nil {
		t.Fatal(err)
	}
	expired, err := store.ClaimNotificationCapability(ctx, expiring.Nonce, expiring.OperationID, expiring.Action, expiring.OperationBinding, expiring.ExpiresAtMS)
	if err != nil {
		t.Fatal(err)
	}
	if expired.Accepted || expired.Reason != "expired" {
		t.Fatalf("expired capability accepted: %+v", expired)
	}
}

func TestAUD_FIX04NotificationBindingMismatchBurnsCapability(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	admission, err := store.AdmitAutomation(ctx, "scan-bind", "ui", "scan", 1, "fp-bind")
	if err != nil {
		t.Fatal(err)
	}
	capability, err := store.CreateNotificationCapability(ctx, admission.OperationID, "cancel", 60_000)
	if err != nil {
		t.Fatal(err)
	}
	bad, err := store.ClaimNotificationCapability(ctx, capability.Nonce, capability.OperationID, capability.Action, "wrong-binding", capability.ExpiresAtMS-1)
	if err != nil {
		t.Fatal(err)
	}
	if bad.Accepted || bad.Reason != "binding_mismatch" {
		t.Fatalf("binding mismatch accepted: %+v", bad)
	}
	burned, err := store.ClaimNotificationCapability(ctx, capability.Nonce, capability.OperationID, capability.Action, capability.OperationBinding, capability.ExpiresAtMS-1)
	if err != nil {
		t.Fatal(err)
	}
	if burned.Accepted || burned.Reason != "replay" {
		t.Fatalf("mismatched capability was not burned: %+v", burned)
	}
}

func TestAUD_FIX04ActivityUnifiesAnalysisAndCleanupHistory(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	analysis, err := store.AdmitAutomation(ctx, "analysis-1", "ui", "analysis", 3, "analysis-fp")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, analysis.OperationID, "running", "analysis_started", "analysis running"); err != nil {
		t.Fatal(err)
	}
	if err := store.UpsertAndroidCleanupOperation(ctx, AndroidCleanupOperation{ID: "cleanup-1", ScanID: "scan-c", RootPath: "/storage", Status: "COMPLETED", SelectedCount: 1, PlannedBytes: 100, DeletedBytes: 100, CreatedAt: 1, ReviewID: "review-c", ScanGeneration: 1, PlanHash: "plan-c", OutcomeCertainty: "CERTAIN"}); err != nil {
		t.Fatal(err)
	}
	runs, err := store.ActivityRuns(ctx, 20)
	if err != nil {
		t.Fatal(err)
	}
	kinds := map[string]ActivityRun{}
	for _, run := range runs {
		kinds[run.Kind] = run
	}
	if kinds["analysis"].OperationID != analysis.OperationID || !kinds["analysis"].Active {
		t.Fatalf("analysis not represented in durable Activity: %+v", kinds["analysis"])
	}
	cleanup := kinds["cleanup"]
	if cleanup.OperationID != "cleanup-1" || cleanup.TerminalTruth != "success" || !cleanup.History {
		t.Fatalf("cleanup not represented in durable Activity: %+v", cleanup)
	}
	if cleanup.CancelGate == "allowed" || cleanup.RetryGate == "allowed" || cleanup.ReconcileGate == "allowed" {
		t.Fatalf("cleanup inherited unsupported generic automation gates: %+v", cleanup)
	}
}

func TestAUD_FIX04CancelRetryReconcileAreGoGated(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	admission, err := store.AdmitAutomation(ctx, "scan-gates", "tasker", "scan", 4, "fp-gates")
	if err != nil {
		t.Fatal(err)
	}
	cancel, err := store.RequestAutomationCancel(ctx, admission.OperationID, "tasker")
	if err != nil {
		t.Fatal(err)
	}
	if !cancel.Allowed || cancel.State != "cancelling" {
		t.Fatalf("cancel gate failed: %+v", cancel)
	}
	if _, err := store.AdvanceAutomation(ctx, admission.OperationID, "cancelled", "cancelled", "cancel observed"); err != nil {
		t.Fatal(err)
	}
	second, err := store.RequestAutomationCancel(ctx, admission.OperationID, "ui")
	if err != nil {
		t.Fatal(err)
	}
	if second.Allowed {
		t.Fatalf("terminal cancel gate reopened: %+v", second)
	}

	failed, err := store.AdmitAutomation(ctx, "scan-failed", "ui", "scan", 7, "fp-retry")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, failed.OperationID, "failure", "failure", "failed before running"); err != nil {
		t.Fatal(err)
	}
	retry, err := store.RetryAutomation(ctx, failed.OperationID, "scan-retry", "ui", 8)
	if err != nil {
		t.Fatal(err)
	}
	if retry.RequestID != "scan-retry" || retry.OperationID == failed.OperationID {
		t.Fatalf("retry did not create new durable identity: %+v", retry)
	}
}

func TestAUD_FIX04RepeatedRunningReportsPersistProgressAndAnalysisCancelIsGated(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	analysis, err := store.AdmitAutomation(ctx, "duplicate-analysis:scan-a:run-1", "ui", "analysis", 9, "analysis-progress-fp")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, analysis.OperationID, "running", "bucket", "bucket 1 of 3"); err != nil {
		t.Fatal(err)
	}
	if _, err := store.AdvanceAutomation(ctx, analysis.OperationID, "running", "bucket", "bucket 2 of 3"); err != nil {
		t.Fatal(err)
	}
	run, err := store.ActivityRunByID(ctx, analysis.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if string(run.Progress) == "" || !strings.Contains(string(run.Progress), "bucket 2 of 3") {
		t.Fatalf("latest repeated-running progress not durable: %s", run.Progress)
	}
	cancel, err := store.RequestAutomationCancel(ctx, analysis.OperationID, "ui")
	if err != nil {
		t.Fatal(err)
	}
	if !cancel.Allowed || cancel.State != "cancelling" {
		t.Fatalf("analysis cancellation gate not executable: %+v", cancel)
	}
}

func TestAUD_FIX04AnalysisActionGatesMatchExecutableAdapters(t *testing.T) {
	ctx := context.Background()
	store, err := OpenSQLiteStore(filepath.Join(t.TempDir(), "dustui.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	generic, err := store.AdmitAutomation(ctx, "analysis-generic", "ui", "analysis", 2, "analysis-generic-fp")
	if err != nil {
		t.Fatal(err)
	}
	genericRun, err := store.ActivityRunByID(ctx, generic.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if genericRun.CancelGate == "allowed" {
		t.Fatalf("generic analysis advertised a cancellation adapter it does not have: %+v", genericRun)
	}

	duplicate, err := store.AdmitAutomation(ctx, "duplicate-analysis:scan-1:nonce", "ui", "duplicate-analysis", 2, "duplicate-fp")
	if err != nil {
		t.Fatal(err)
	}
	duplicateRun, err := store.ActivityRunByID(ctx, duplicate.OperationID)
	if err != nil {
		t.Fatal(err)
	}
	if duplicateRun.CancelGate != "allowed" {
		t.Fatalf("duplicate analysis does not expose its executable cancellation adapter: %+v", duplicateRun)
	}
	action, err := store.RequestAutomationCancel(ctx, duplicate.OperationID, "ui")
	if err != nil {
		t.Fatal(err)
	}
	if !action.Allowed || action.State != "cancelling" {
		t.Fatalf("duplicate analysis cancellation was not Go-gated: %+v", action)
	}
}
