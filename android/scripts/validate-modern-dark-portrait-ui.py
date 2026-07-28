#!/usr/bin/env python3
"""Source audit for the DustUI modern dark portrait roadmap seal."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CHECKS: list[tuple[str, str, str]] = [
    ("Theme.kt", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/Theme.kt", "object DustModernColors"),
    ("ModernDustComponents.kt", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/ModernDustComponents.kt", "fun DustHeroCard"),
    ("compact nav Home", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt", "DustDestination.INSIGHTS"),
    ("compact nav Clean", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt", "DustDestination.RECOMMENDATIONS"),
    ("scan sheet", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "private fun ScanLauncherSheet"),
    ("scan sheet opt-in", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "@OptIn(ExperimentalMaterial3Api::class)"),
    ("home dashboard", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "private fun HomeDashboardScreen"),
    ("files local search", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "Current folder search"),
    ("whole-scan search", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "Whole-scan search"),
    ("clean cockpit", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/CleanupRecommendationsScreen.kt", "Cleanup cockpit"),
    ("tools hub", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "private fun ToolsHubScreen"),
    ("module diagnostics", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "Current scan does not include modules"),
    ("module scan action", "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt", "Scan /data/adb/modules"),
    ("final UI contract", "UI_UX_CONTRACT.md", "Modern dark portrait final qualification contract"),
    ("final seal doc", "docs/ANDROID_MODERN_DARK_PORTRAIT_FINAL_SEAL.md", "Final compact navigation contract"),
]

FORBIDDEN: list[tuple[str, str, str]] = [
    ("stale module wording", "docs/ANDROID_MODERN_DARK_PORTRAIT_UI.md", "Current scan excludes modules"),
    ("stale five-tab compact contract", "UI_UX_CONTRACT.md", "five core destinations"),
    ("stale scan-back contract", "UI_UX_CONTRACT.md", "primary workbenches return to Scan"),
]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.exists():
        raise AssertionError(f"missing {relative}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []
    for name, relative, token in CHECKS:
        try:
            text = read(relative)
        except AssertionError as exc:
            failures.append(f"{name}: {exc}")
            continue
        if token not in text:
            failures.append(f"{name}: missing token {token!r} in {relative}")
    for name, relative, token in FORBIDDEN:
        try:
            text = read(relative)
        except AssertionError as exc:
            failures.append(f"{name}: {exc}")
            continue
        if token in text:
            failures.append(f"{name}: forbidden stale token {token!r} in {relative}")
    if failures:
        print("Modern dark portrait final audit failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("Modern dark portrait final audit passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
