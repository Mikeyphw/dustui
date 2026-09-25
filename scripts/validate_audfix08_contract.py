#!/usr/bin/env python3
"""AUD-FIX-08 semantic navigation cutover contract.

Read-only source contract used as a Devtool leaf. Runtime/JVM behavior is covered by
DustSemanticNavigationTest and go/internal/shell route tests.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "overview/home", "overview/composition", "overview/trends", "overview/scan-history",
    "files/explorer", "files/search", "files/large", "files/treemap", "files/item",
    "clean/recommended", "clean/duplicates", "clean/residue", "clean/rules", "clean/candidate", "clean/review",
    "apps/list", "apps/package", "apps/paths",
    "activity/list", "activity/run", "activity/evidence",
    "system/runtime", "system/modules", "system/automation", "system/diagnostics", "system/certification", "system/settings",
}


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.is_file():
        raise AssertionError(f"missing required file: {rel}")
    return path.read_text(encoding="utf-8")


def route_literals(text: str) -> set[str]:
    return set(re.findall(r'"((?:overview|files|clean|apps|activity|system)/[a-z-]+)"', text))


def require(text: str, token: str, label: str) -> None:
    if token not in text:
        raise AssertionError(f"{label}: missing {token!r}")


def reject(text: str, token: str, label: str) -> None:
    if token in text:
        raise AssertionError(f"{label}: predecessor token still reachable: {token!r}")


def main() -> int:
    android_routes = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustSemanticNavigation.kt")
    go_routes = read("go/internal/shell/routes.go")
    app = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    design = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt")
    activity = read("android/app/src/main/kotlin/com/mikeyphw/dustui/MainActivity.kt")
    manifest = read("android/app/src/main/AndroidManifest.xml")
    android_test = read("android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DustSemanticNavigationTest.kt")
    go_test = read("go/internal/shell/routes_test.go")
    devtool = read(".devtool.toml")

    if route_literals(android_routes) != EXPECTED:
        raise AssertionError(f"Android semantic inventory mismatch: {sorted(route_literals(android_routes) ^ EXPECTED)}")
    if route_literals(go_routes) != EXPECTED:
        raise AssertionError(f"Go semantic inventory mismatch: {sorted(route_literals(go_routes) ^ EXPECTED)}")

    for token in ("DustDestination", "DustShellNavigation", "ToolsHubScreen", "DebugWorkbench"):
        reject(app + design, token, "production Android navigation")

    for token in ("rememberSaveable", "routeName", "routeEntity", "routeGeneration", "DustSemanticNavigation.backTarget"):
        require(app, token, "saved semantic route identity")
    for token in ("EntityNotFound", "EntityStale", "EntityRequired", "resolveDeepLink", "rawPath"):
        require(android_routes, token, "typed Android deep-link contract")
    for token in ("EscapedPath", "RouteError", "entity_required", "SemanticRoutes", "PrimaryRoutes"):
        require(go_routes, token, "typed Go route contract")

    require(activity, "onNewIntent", "Android deep-link lifecycle")
    require(activity, "incomingDeepLink", "Android deep-link lifecycle")
    require(manifest, "android.intent.action.VIEW", "Android manifest deep link")
    require(manifest, 'android:scheme="dustui"', "Android manifest deep link")

    for renderer in ("ActivitySemanticScreen", "SystemRuntimeScreen", "SystemAutomationScreen", "DiagnosticsAuthorityScreen", "SystemSettingsScreen"):
        require(app, renderer, "decomposed semantic surfaces")

    for token in ("27", "deep links round trip", "saved semantic identity restores", "compact and wide chrome cannot remap"):
        require(android_test, token, "Android semantic behavior tests")
    for token in ("AUD_FIX08", "RouteFilesItem", "RejectsMissingEntity", "PrimaryRoutes"):
        require(go_test, token, "Go semantic behavior tests")

    require(devtool, "aud_fix_08", "Devtool AUD-FIX-08 workflow")
    require(devtool, "audfix08_android_contract", "Devtool AUD-FIX-08 Android leaf")
    require(devtool, "audfix08_go_routes", "Devtool AUD-FIX-08 Go leaf")

    print("AUD-FIX-08 semantic navigation contract: PASS")
    print(f"semantic routes: {len(EXPECTED)}; primary domains: 6")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"AUD-FIX-08 semantic navigation contract: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
