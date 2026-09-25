#!/usr/bin/env python3
"""AUD-FIX-09 bound async state + typed action-gate contract."""
from __future__ import annotations
import sys, tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    p = ROOT / rel
    if not p.is_file(): raise AssertionError(f"missing required file: {rel}")
    return p.read_text(encoding="utf-8")

def require(text: str, token: str, label: str) -> None:
    if token not in text: raise AssertionError(f"{label}: missing {token!r}")

def reject(text: str, token: str, label: str) -> None:
    if token in text: raise AssertionError(f"{label}: retired predecessor still present: {token!r}")

def main() -> int:
    go_async = read("go/internal/shell/async.go")
    go_test = read("go/internal/shell/async_test.go")
    runtime = read("go/cmd/dustui-go/runtime_domain.go")
    contract = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustShellRouteContract.kt")
    registry = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustRouteRequestRegistry.kt")
    vm = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    app = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    product = read("android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductCutoverContract.kt")
    android_test = read("android/app/src/test/kotlin/com/mikeyphw/dustui/ui/shell/DustRouteAsyncProtocolTest.kt")
    devtool = read(".devtool.toml")

    for token in ("RouteKey", "RouteGeneration", "EntityKind", "EntityID", "RequestID", "Fingerprint"):
        require(go_async, token, "Go binding tuple")
    for token in ("routeKey", "routeGeneration", "entityKind", "entityId", "requestId", "fingerprint"):
        require(contract, token, "Android binding tuple")
    for token in ("Allowed", "ReasonCode", "Message", "RemediationActions", "EvaluateActionGate"):
        require(go_async, token, "Go typed action gate")
    for token in ("allowed", "reasonCode", "message", "remediationActions"):
        require(contract, token, "Android typed action gate")
    reject(contract, "enum class DustActionGate", "Android gate policy")
    reject(product, "RequiredActionGates", "product predecessor gate enum")
    require(product, "RequiredActionGateFields", "product structured gate contract")

    for state in ("Loading", "Empty", "Blocked", "Error", "Content"):
        require(contract, f"{state}", "shared Android screen states")
    for token in ("DustScreenState.Loading", "DustScreenState.Empty", "DustScreenState.Blocked", "DustScreenState.Error"):
        require(app, token, "Compose shared state consumption")
    for retired in ("FilesNoScanScreen", "CleanNoScanScreen", "ScanRequiredEmptyState"):
        reject(app, retired, "local predecessor presentation state")

    reject(vm, "AtomicLong", "MainViewModel async ownership")
    for token in ("DustRouteRequestRegistry", "issueRouteRequest", "acceptsRouteResponse", "refreshRoutePresentation", 'routeRequests.issue("screen"'):
        require(vm + registry, token, "Android bound request authority")
    for token in ('goActionGate(', 'action = "scan.cancel"', 'action = "cleanup.review"', 'action = "cleanup.execute"', 'action = "cleanup.analyze"', 'action = "files.export"', 'action = "certification.run"', 'action = "activity.cancel"', 'action = "activity.retry"', 'action = "activity.reconcile"'):
        require(vm, token, "Go-owned risky/evidence action gates")
    reject(vm, "\n        if (_scanDataStale.value)", "local stale action policy")
    reject(app, "enabled = !scanDataStale", "Compose local stale action policy")
    reject(app, "staleDeletionDisabledReason", "predecessor local deletion gate")
    for token in ('"domain.ui.action-gate"', '"domain.ui.screen-state"'):
        require(runtime, token, "Go protocol operations")

    for token in ("directory-query", "package-switch", "cleanup-review", "activity-selection", "diagnostics-generation", "req-late"):
        require(go_test, token, "adversarial Go stale-response coverage")
    for token in ("route entity request and fingerprint drift", "entity switch invalidates package cleanup and activity work", "blocked remains distinct"):
        require(android_test, token, "Android stale/state coverage")

    cfg = tomllib.loads(devtool)
    aw = cfg["targets"]["dustui_android"]["workflows"]
    gw = cfg["targets"]["dustui_go"]["workflows"]
    if "aud_fix_09" not in aw or "aud_fix_09" not in gw:
        raise AssertionError("Devtool AUD-FIX-09 workflows missing")
    require(devtool, "audfix09_async_state_contract", "Android Devtool leaf")
    require(devtool, "audfix09_async_protocol", "Go Devtool leaf")
    require(devtool, '[wrapper.commands.aud-fix-09]', "wrapper command")

    print("AUD-FIX-09 bound async state + typed action-gate contract: PASS")
    return 0

if __name__ == "__main__":
    try: raise SystemExit(main())
    except AssertionError as exc:
        print(f"AUD-FIX-09 contract: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
