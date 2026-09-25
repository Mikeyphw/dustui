#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "migration/audit/v2/aud_fix_04_audit_loop.json"
LEDGER = ROOT / "migration/ledger/aud_fix_04_status.json"


def text(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def require(failures: list[dict[str, str]], ok: bool, code: str, detail: str) -> None:
    if not ok:
        failures.append({"code": code, "detail": detail})


def check_source() -> list[dict[str, str]]:
    f: list[dict[str, str]] = []
    state = text("go/internal/state/automation_authority.go")
    state_test = text("go/internal/state/automation_authority_test.go")
    cleanup_state = text("go/internal/state/android_domain.go")
    schema = text("go/internal/state/schema.go")
    runtime = text("go/cmd/dustui-go/runtime_domain.go")
    adapter = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoAutomationAdapter.kt")
    models = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ActivityModels.kt")
    repo = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    vm = text("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    service = text("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt")
    worker = text("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScheduledScanWorker.kt")
    tasker = text("android/app/src/main/kotlin/com/mikeyphw/dustui/tasker/DustTaskerReceiver.kt")
    android_test = text("android/app/src/test/kotlin/com/mikeyphw/dustui/automation/AudFix04AutomationContractTest.kt")
    root_diag_test = text("android/app/src/test/kotlin/com/mikeyphw/dustui/data/RootDiagnosticsSourceContractTest.kt")
    ov01_test = text("android/app/src/test/kotlin/com/mikeyphw/dustui/migration/Ov01MigrationContractTest.kt")
    root_diag_validator = text("android/scripts/validate-root-scan-diagnostics.py")

    # Durable authority and same-operation identity.
    for token in [
        'AutomationProtocol         = "dustui.automation.activity.v2"',
        '"accepted":', '"success":', '"failure":', '"cancelled":', '"recovery_required":',
        "accepted_not_success", "terminal_truth", "evidence_correlation",
        "automation_sources", "activity_runs", "notification_actions",
    ]:
        require(f, token in state or token in schema, "AUD011_DURABLE_AUTHORITY", f"durable operation contract missing {token}")
    require(f, "stableProductID(requestID, kind" in state and "stableProductID(requestID, source" not in state,
            "AUD011_SHARED_OPERATION_ID", "operation identity is not adapter-source independent")
    require(f, 'case "scan", "status", "inspect", "analysis", "duplicate-analysis":' in state,
            "AUD011_OPERATION_KINDS", "durable coordinator cannot distinguish generic and cancellable duplicate analyses")
    require(f, "terminalAutomationStates[from]" in state and "automation_transition_invalid" in state,
            "AUD011_TERMINAL_STICKY", "terminal operation truth is not transition guarded")
    require(f, 'kind != "scan" && kind != "duplicate-analysis"' in state and 'cancel = "blocked_kind"' in state,
            "AUD011_CANCEL_GATE_KIND", "Go cancel gate is advertised for operation kinds without an executable cancellation adapter")

    # Runtime reachability and Activity durability.
    for op in [
        "domain.automation.admit", "domain.automation.advance", "domain.automation.observe", "domain.automation.by-request",
        "domain.automation.activity.list", "domain.automation.activity.detail", "domain.automation.cancel",
        "domain.automation.reconcile", "domain.automation.retry", "domain.automation.notification.create",
        "domain.automation.notification.claim",
    ]:
        require(f, op in runtime, "AUD011_RUNTIME_REACHABILITY", f"Go runtime operation missing: {op}")
    require(f, "syncCleanupActivity" in cleanup_state and "android_cleanup_operations c" in state,
            "AUD011_CLEANUP_ACTIVITY", "cleanup run truth is not projected into durable Activity")
    for blocked in ["blocked_cleanup_review_required", "blocked_cleanup_owned", "blocked_cleanup_recovery_surface"]:
        require(f, blocked in cleanup_state, "AUD011_CLEANUP_ACTION_TRUTH", f"cleanup Activity falsely lacks explicit blocked gate {blocked}")

    # Android UI and adapter consumption.
    for op in ["domain.automation.activity.list", "domain.automation.activity.detail", "domain.automation.cancel", "domain.automation.retry", "domain.automation.reconcile"]:
        require(f, op in adapter, "AUD011_ANDROID_ADAPTER", f"Android durable Activity adapter missing {op}")
    for token in ["automation_retry_kind_unsupported", "ScanRequest.fromJson(previousSession.optionsJson)", "operationId = admitted.operationId"]:
        require(f, token in adapter, "AUD011_RETRY_EXECUTION", f"retry admits durable identity but does not restart the bound scan: {token}")
    require(f, "previous.Kind != \"scan\"" in state and "automation_retry_kind_unsupported" in state,
            "AUD011_RETRY_GATE_KIND", "Go retry gate can admit an operation kind without an executable retry adapter")
    for token in ["suspend fun activityRuns", "suspend fun activityRun", "suspend fun cancelActivityRun", "suspend fun reconcileActivityRun", "suspend fun retryActivityRun"]:
        require(f, token in repo, "AUD011_ANDROID_REPOSITORY", f"repository durable Activity bridge missing {token}")
    for token in [
        'trackedAnalysis(scanId, "residue")',
        'trackedAnalysis(scanId, "advanced-storage")',
        'trackedAnalysis(scanId, "app-storage")',
        'trackedAnalysis(scanId, "modules")',
        'trackedAnalysis(scanId, "recommendations")',
        'trackedAnalysis(scanId, "rule-preview")',
        'trackedAnalysis(current.id, "storage-growth")',
        'kind = "duplicate-analysis"',
    ]:
        require(f, token in repo, "AUD011_PRODUCT_ANALYSIS_DURABILITY", f"product analysis flow is not entering durable Activity truth: {token}")
    require(f, '"duplicate-analysis" ->' in repo and "cancelDuplicateAnalysis(scanId)" in repo,
            "AUD011_ANALYSIS_CANCEL_EXECUTION", "duplicate-analysis cancel gate is not bound to its real root cancellation adapter")
    require(f, 'cancelOperation(operationId, "ui")' in repo and "ScanForegroundService.cancel" in adapter,
            "AUD011_UI_CANCEL_EXECUTION", "UI cancel changes durable state without executing the bound scan cancellation path")
    require(f, "recoverInterruptedOperations" in repo and "adapter.activity(500)" in repo and "adapter.reconcile(run.operationId)" in repo,
            "AUD011_PROCESS_DEATH_RECONCILE", "startup recovery does not reconcile durable scan operations after process death")
    for token in ["val activityRuns: StateFlow<List<ActivityRun>>", "val selectedActivityRun: StateFlow<ActivityRun?>", "fun refreshActivityRuns", "fun cancelActivityRun", "fun reconcileActivityRun", "fun retryActivityRun"]:
        require(f, token in vm, "AUD011_UI_OPERATION_TRUTH", f"Android UI state does not consume durable operation truth: {token}")
    require(f, "val canReconcile: Boolean get() = reconcileGate == \"allowed\"" in models,
            "AUD011_UI_GATE_TRUTH", "Android Activity model bypasses Go reconcile gate")

    # WorkManager / Tasker must wait for terminal truth.
    for token in ["runScanToTerminal", "terminal.terminalTruth", "KEY_OPERATION_ID"]:
        require(f, token in worker, "AUD011_WORKMANAGER_TERMINAL", f"WorkManager durable terminal behavior missing {token}")
    require(f, "STATUS_LAUNCHED_NOT_TERMINAL" not in worker and "Result.success" in worker,
            "AUD011_WORKMANAGER_NO_LAUNCH_SUCCESS", "WorkManager still carries predecessor launch-success semantics")
    for token in ["goAsync()", "PendingResult", "adapter.startScan", "ACTION_STATUS", "requestScanCancel", "RESULT_TERMINAL", "RESULT_PENDING", "RESULT_OPERATION_ID"]:
        require(f, token in tasker, "AUD011_TASKER_TERMINAL", f"Tasker durable operation/status behavior missing {token}")
    require(f, "runScanToTerminal" not in tasker and "cancelScanToTerminal" not in tasker,
            "AUD011_TASKER_BROADCAST_LIFETIME", "Tasker receiver holds a PendingResult while waiting for a long-running scan")
    require(f, 'RESULT_STATUS to "accepted"' not in tasker and 'RESULT_STATUS to "cancel_requested"' not in tasker,
            "AUD011_TASKER_NO_ADMISSION_SUCCESS", "Tasker still reports admission/cancel-request as terminal truth")

    # Foreground scan reports lifecycle and validates supplied operation binding.
    for token in ["automation.report(operation.operationId, \"failure\"", "automation.report(id, terminalState", "requestCancel(operationId, source)",
                  "AUTOMATION_OPERATION_BINDING_MISMATCH", "observed.requestId != request.scanId"]:
        require(f, token in service, "AUD011_SCAN_OPERATION_BINDING", f"foreground service operation binding/lifecycle missing {token}")
    require(f, "session = session.copy(generation = durableOperation.generation)" in service and "generation = operation.generation" in service,
            "AUD011_GENERATION_BINDING", "scan session generation diverges from durable operation generation, especially on retry")

    # Notification capability: nonce, TTL/binding, burn-on-invalid, one-use.
    for token in ["crypto/rand", "expires_at_ms", "operation_binding", "claimed_at_ms", "replay_rejected", "binding_mismatch", "COALESCE(claimed_at_ms,?)"]:
        require(f, token in state or token in schema, "AUD012_NOTIFICATION_CAPABILITY", f"notification capability invariant missing {token}")
    for token in ["EXTRA_NOTIFICATION_NONCE", "EXTRA_NOTIFICATION_BINDING", "claimNotificationCancel", "SOURCE_NOTIFICATION"]:
        require(f, token in service, "AUD012_NOTIFICATION_ANDROID", f"notification PendingIntent/claim missing {token}")
    require(f, "domain.automation.notification.create" in adapter and "domain.automation.notification.claim" in adapter,
            "AUD012_NOTIFICATION_REACHABILITY", "Android does not use Go notification capability issue/claim")

    # Reconnect/process-death and action-gate fixtures.
    for token in [
        "TestAUD_FIX04DurableOperationSurvivesRestartAndSharesAdapterIdentity",
        "store.Close()", "ReconcileAutomation", "COMPLETED",
        "TestAUD_FIX04NotificationCapabilityIsBoundSingleUseAndExpiring",
        "TestAUD_FIX04NotificationBindingMismatchBurnsCapability",
        "TestAUD_FIX04ActivityUnifiesAnalysisAndCleanupHistory",
        "TestAUD_FIX04CancelRetryReconcileAreGoGated",
        "TestAUD_FIX04AnalysisActionGatesMatchExecutableAdapters",
    ]:
        require(f, token in state_test, "AUD011_AUD012_BEHAVIOR_FIXTURE", f"Go lifecycle/replay/reconnect fixture missing {token}")
    require(f, "AudFix04AutomationContractTest" in android_test and "activityRuns" in android_test,
            "AUD011_ANDROID_CONTRACT_TEST", "Android WorkManager/Tasker/Activity contract fixture missing")

    # Same-overlay source/token guard retirement.
    require(f, "STATUS_LAUNCHED_NOT_TERMINAL" in ov01_test and "assertFalse" in ov01_test,
            "AUD016_OV01_SUCCESSOR_GUARD", "OV01 predecessor test was not rewritten to reject launch-success semantics")
    require(f, 'RESULT_STATUS to \\"accepted\\"' in root_diag_test or 'RESULT_STATUS to "accepted"' in root_diag_test,
            "AUD016_TASKER_SUCCESSOR_GUARD", "root diagnostics test no longer checks predecessor Tasker admission semantics")
    require(f, 'reject(tasker, \'RESULT_STATUS to "accepted"\'' in root_diag_validator,
            "AUD016_VALIDATOR_SUCCESSOR_GUARD", "root diagnostics validator still lacks successor rejection of Tasker accepted-as-success")

    # Gate D must remain open.
    q = ROOT / "migration/qualification/v1/current_state.json"
    if q.is_file():
        obj = json.loads(q.read_text())
        require(f, obj.get("gate_d", {}).get("qualified") is False,
                "ROADMAP_GATE_D_MUST_STAY_OPEN", "AUD-FIX-04 must not qualify Gate D")
    return f


def run_go_fast_tests() -> dict:
    cp = subprocess.run(
        ["go", "test", "./internal/automation"],
        cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        env={**__import__('os').environ, "GOTOOLCHAIN": "local", "GOPROXY": "off"},
    )
    return {"status": "passed" if cp.returncode == 0 else "failed", "output": cp.stdout[-12000:]}


def modernc_available() -> bool:
    cp = subprocess.run(["go", "env", "GOMODCACHE"], cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    if cp.returncode != 0:
        return False
    cache = Path(cp.stdout.strip())
    return any(cache.glob("modernc.org/sqlite@v1.36.3*")) or any((cache / "cache/download/modernc.org/sqlite/@v").glob("v1.36.3.zip"))


def run_state_tests() -> dict:
    if not modernc_available():
        return {"status": "environment-deferred", "reason": "modernc.org/sqlite v1.36.3 is absent from this sandbox module cache; CGO sqlite substitution is forbidden"}
    cp = subprocess.run(
        ["go", "test", "./internal/state", "-run", "^TestAUD_FIX04", "-count=1"],
        cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        env={**__import__('os').environ, "GOTOOLCHAIN": "local", "GOPROXY": "off"},
    )
    return {"status": "passed" if cp.returncode == 0 else "failed", "output": cp.stdout[-16000:]}


def gradle_distribution_available() -> bool:
    # This sandbox does not have network. Only execute Gradle when the exact wrapper is already cached.
    props = text("android/gradle/wrapper/gradle-wrapper.properties")
    version = ""
    if "gradle-" in props and "-bin.zip" in props:
        version = props.split("gradle-", 1)[1].split("-bin.zip", 1)[0]
    if not version:
        return False
    home = Path.home() / ".gradle/wrapper/dists"
    return home.exists() and any(p.name.startswith("gradle-") and p.is_file() for p in home.rglob("gradle-launcher-*.jar"))


def run_android_contract_test() -> dict:
    if not gradle_distribution_available():
        return {"status": "environment-deferred", "reason": "Gradle wrapper distribution is not cached in this network-isolated sandbox"}
    cp = subprocess.run(
        ["./gradlew", ":app:testDebugUnitTest", "--tests", "com.mikeyphw.dustui.automation.AudFix04AutomationContractTest", "--offline", "--no-daemon"],
        cwd=ROOT / "android", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return {"status": "passed" if cp.returncode == 0 else "failed", "output": cp.stdout[-16000:]}


def run_pass(index: int) -> dict:
    failures = check_source()
    go_fast = run_go_fast_tests()
    state_tests = run_state_tests()
    android = run_android_contract_test()
    if go_fast["status"] == "failed":
        failures.append({"code": "AUD_FIX_04_GO_FAST_TEST", "detail": "non-SQLite Go automation tests failed"})
    if state_tests["status"] == "failed":
        failures.append({"code": "AUD_FIX_04_STATE_TEST", "detail": "durable SQLite lifecycle/replay tests failed"})
    if android["status"] == "failed":
        failures.append({"code": "AUD_FIX_04_ANDROID_TEST", "detail": "Android WorkManager/Tasker/Activity contract test failed"})
    return {"pass": index, "failures": failures, "go_fast": go_fast, "state_tests": state_tests, "android_contract_test": android}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    ap.add_argument("--write-evidence", action="store_true")
    args = ap.parse_args()
    passes: list[dict] = []
    previous: tuple[str, ...] | None = None
    converged = False
    for i in range(1, 6 if args.audit_loop else 2):
        result = run_pass(i)
        passes.append(result)
        codes = tuple(x["code"] for x in result["failures"])
        if not codes and previous == codes:
            converged = True
            break
        if not args.audit_loop and not codes:
            converged = True
            break
        if previous == codes:
            converged = not codes
            break
        previous = codes
    failures = passes[-1]["failures"]
    deferred = []
    for name, key in [("modernc SQLite lifecycle/replay tests", "state_tests"), ("Android Gradle contract test", "android_contract_test")]:
        if passes[-1][key].get("status") == "environment-deferred":
            deferred.append(name)
    evidence = {
        "schema": "dustui.aud-fix-04-audit.v1",
        "overlay": "AUD-FIX-04",
        "scope_gap_families": ["AUD-011", "AUD-012", "AUD-016"],
        "passes": passes,
        "converged": converged and not failures,
        "environment_deferred_evidence": deferred,
        "later_roadmap_gaps_intentionally_open": [
            "AUD-003", "AUD-004", "AUD-005", "AUD-013", "AUD-015", "AUD-016", "AUD-017", "AUD-018", "AUD-019", "AUD-020", "AUD-021", "AUD-022", "AUD-023", "AUD-024",
            "UIX-001", "UIX-002", "UIX-003", "UIX-004", "UIX-005", "UIX-006", "UIX-008", "UIX-009",
        ],
        "gate_d": "open",
        "activity_ui_note": "durable UI state/adapters are wired in AUD-FIX-04; final Activity route/list/detail UX remains assigned to AUD-FIX-11",
    }
    ledger = {
        "schema": "dustui.aud-fix-status.v1",
        "overlay": "AUD-FIX-04",
        "status": "implementation-complete-evidence-deferred" if evidence["converged"] and deferred else ("complete" if evidence["converged"] else "blocked"),
        "closed_or_advanced_gap_families": ["AUD-011", "AUD-012", "AUD-016"] if evidence["converged"] else [],
        "audit_evidence": "migration/audit/v2/aud_fix_04_audit_loop.json",
        "gate_d": "open",
        "passes": len(passes),
        "blockers": failures,
        "environment_deferred_evidence": deferred,
    }
    if args.write_evidence:
        EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
        LEDGER.parent.mkdir(parents=True, exist_ok=True)
        EVIDENCE.write_text(json.dumps(evidence, indent=2) + "\n")
        LEDGER.write_text(json.dumps(ledger, indent=2) + "\n")
    for p in passes:
        print(f"AUD-FIX-04 audit pass {p['pass']}: failures={len(p['failures'])} state={p['state_tests']['status']} android={p['android_contract_test']['status']}")
        for failure in p["failures"]:
            print(f"  - {failure['code']}: {failure['detail']}")
    print(f"converged={str(evidence['converged']).lower()}")
    return 0 if evidence["converged"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
