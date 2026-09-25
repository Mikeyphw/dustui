#!/usr/bin/env python3
"""AUD-FIX-12 adaptive layout, route continuity, and accessibility contract."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def require(text: str, token: str, label: str, failures: list[str]) -> None:
    if token not in text:
        failures.append(f"{label}: missing {token!r}")


def reject(text: str, token: str, label: str, failures: list[str]) -> None:
    if token in text:
        failures.append(f"{label}: forbidden {token!r}")


def adaptive_checks(failures: list[str]) -> None:
    policy = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustAdaptiveLayout.kt")
    pane = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustAdaptivePane.kt")
    app = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    tests = read("android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DustAdaptiveLayoutTest.kt")
    for token in [
        "enum class DustWindowClass",
        "enum class DustPaneMode",
        "DustPaneMode.MasterDetail",
        "DustPaneMode.Supporting",
        "fun preserveSelection",
        "DustRouteDomain.Files",
        "DustRouteDomain.Clean",
        "DustRouteDomain.Apps",
        "DustRouteDomain.Activity",
        "DustRouteDomain.System",
    ]:
        require(policy, token, "adaptive policy", failures)
    for token in [
        "DustAdaptiveLayoutPolicy.resolve(maxWidth.value, maxHeight.value, selection)",
        "DustAdaptiveMasterPane(",
        "adaptiveLayout.showsMasterPane",
        "adaptiveLayout.usesNavigationRail",
        ".dustDetailTraversal()",
    ]:
        require(app, token, "production shell", failures)
    for token in ["@Preview(name = \"AUD-FIX-12 phone\"", "@Preview(name = \"AUD-FIX-12 tablet\"", "traversalIndex = 1f"]:
        require(pane, token, "adaptive pane", failures)
    for token in ["phone tablet and rotation never remap semantic identity", "master selection is derived from semantic route not layout local state"]:
        require(tests, token, "adaptive unit tests", failures)
    reject(app, "val wide = maxWidth >= 840.dp", "predecessor width-only shell heuristic", failures)


def continuity_checks(failures: list[str]) -> None:
    policy = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustAdaptiveLayout.kt")
    app = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    nav = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustSemanticNavigation.kt")
    device = read("android/app/src/androidTest/kotlin/com/mikeyphw/dustui/ui/AdaptiveRouteContinuityInstrumentedTest.kt")
    devtool = read(".devtool.toml")
    for token in ["return selection", "route == selection.route && entityId == selection.entityId", "DustRouteSelection(route, entityId"]:
        require(policy, token, "route-owned adaptive identity", failures)
    require(app, "rememberSaveable { mutableStateOf(DustSemanticRoute.OverviewHome.name) }", "saved route identity", failures)
    require(app, "rememberSaveable { mutableStateOf<String?>(null) }", "saved entity identity", failures)
    require(nav, "fun deepLink(selection: DustRouteSelection)", "semantic deep link", failures)
    for token in ["semanticDetailSurvivesPhoneTabletAndRotationPolicies", "focusTraversalOrderIsStableAcrossWideLayouts"]:
        require(device, token, "device continuity tests", failures)
    for token in ["android_adaptive_device", "aud_fix_12_device"]:
        require(devtool, token, "Devtool device evidence binding", failures)

    # Resize/layout effects may record diagnostics, but must not navigate or replace entity identity.
    marker = "LaunchedEffect(selection.route, selection.entityId, selection.generation, adaptiveLayout.windowClass)"
    if marker in app:
        body = app.split(marker, 1)[1].split("}", 1)[0]
        if "navigate(" in body or "routeName =" in body or "routeEntity =" in body:
            failures.append("window-class effect mutates semantic navigation identity")
    else:
        failures.append("window-class continuity effect missing")


def accessibility_checks(failures: list[str]) -> None:
    policy = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustAdaptiveLayout.kt")
    pane = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustAdaptivePane.kt")
    charts = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/StorageCharts.kt")
    treemap = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/Treemap.kt")
    app = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    tests = read("android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DustAdaptiveLayoutTest.kt")
    for token in ["object DustVisualAccessibility", "fun storageComposition", "fun treemap"]:
        require(policy, token, "visual text authority", failures)
    require(charts, "DustVisualAccessibility.storageComposition(slices)", "chart textual equivalent", failures)
    require(treemap, "DustVisualAccessibility.treemap(shown)", "treemap textual equivalent", failures)
    for token in ["isTraversalGroup = true", "traversalIndex = 0f", "traversalIndex = 1f", "traversalIndex = 2f"]:
        if token not in pane and token not in app:
            failures.append(f"focus/traversal semantics missing {token!r}")
    require(tests, "visual data has exact textual equivalents", "accessibility unit tests", failures)

    # Product acceptance invariants: states stay typed/distinct and destructive execution is review-only.
    for token in [
        "DustScreenState.Loading",
        "DustScreenState.Empty",
        "DustScreenState.Blocked",
        "DustScreenState.Error",
        "CleanupReviewSheet(",
        "execute = viewModel::executeCleanup",
    ]:
        require(app, token, "product acceptance", failures)
    if app.count("viewModel::executeCleanup") != 1:
        failures.append("destructive cleanup execution must have exactly one production UI binding")
    if "Advanced diagnostics" not in app:
        failures.append("developer-oriented diagnostics are not behind explicit progressive disclosure")


def docs_checks(failures: list[str]) -> None:
    for rel in [
        "migration/contracts/v2/aud_fix_12_adaptive_route_continuity.md",
        "migration/ledger/aud_fix_12_status.json",
        "migration/audit/v2/aud_fix_12_audit_loop.json",
        "UI_UX_CONTRACT.md",
        "VALIDATION.md",
    ]:
        if not (ROOT / rel).is_file():
            failures.append(f"missing AUD-FIX-12 evidence/document: {rel}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--section", choices=["adaptive", "continuity", "accessibility", "all"], default="all")
    args = parser.parse_args()
    failures: list[str] = []
    if args.section in ("adaptive", "all"):
        adaptive_checks(failures)
    if args.section in ("continuity", "all"):
        continuity_checks(failures)
    if args.section in ("accessibility", "all"):
        accessibility_checks(failures)
    if args.section == "all":
        docs_checks(failures)
    if failures:
        print("AUD-FIX-12 contract failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print(f"AUD-FIX-12 {args.section} contract passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
