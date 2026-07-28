#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()

REQUIRED = {
    "android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugEvents.kt": [
        "const val DUST_DEBUG_EVENT_LIMIT = 500",
        "enum class DebugCategory",
        "enum class DebugSeverity",
        "enum class DebugProbeTarget",
        "class DebugEventStore",
        "fun String.redactDebugValue()",
    ],
    "android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt": [
        "enum class DebugExportMode",
        "DebugExportMode.Safe",
        "dustui-debug-report.json",
        "trace-events.jsonl",
        "probe-results.jsonl",
        "scan-summary.json",
        "root-diagnostics.json",
        "navigation-state.json",
        "cleanup-state.json",
        "recommendation-summary.json",
        "module-diagnostics.json",
        "app-info.json",
        "noFileContents",
    ],
    "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt": [
        "DEBUG(\"Debug workbench\", \"Debug\"",
        "DustDestination.DEBUG",
        "fun needsScanData(destination: DustDestination): Boolean",
    ],
    "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt": [
        "val debugEvents: StateFlow<List<DebugEvent>>",
        "val debugProbeResults: StateFlow<List<DebugProbeResult>>",
        "val debugExportResult: StateFlow<DebugExportResult?>",
        "val debugOverlayVisible: StateFlow<Boolean>",
        "fun runAllDebugProbes()",
        "fun exportDebugBundle(mode: DebugExportMode",
        "fun setDebugOverlayVisible(visible: Boolean)",
    ],
    "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt": [
        "DebugWorkbenchScreen(",
        "Live state",
        "Trace timeline",
        "Diagnostic probes",
        "Export debug bundle",
        "UI debug overlay",
        "DebugOverlayHud(",
        "private data class DisabledActionReason",
        "private fun DisabledReasonNotice",
    ],
    "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DebugWorkbenchSourceContractTest.kt": [
        "phase 9 debug event store is bounded and redacts sensitive evidence",
        "phase 10 exposes debug workbench from tools without scan data",
        "phase 11 diagnostic probes define evidence only targets",
        "phase 12 defines safe path aware and full debug export modes",
        "phase 13 exposes a floating UI debug overlay toggle",
    ],
    "android/app/src/test/kotlin/com/mikeyphw/dustui/ui/DebugWorkbenchFinalContractTest.kt": [
        "phase 14 seals the debug route as scan independent tools state",
        "phase 14 locks the full debug roadmap contracts",
    ],
    "docs/ANDROID_DEBUG_WORKBENCH.md": [
        "Phase 9 — Debug event model and recorder",
        "Phase 10 — Debug Workbench UI",
        "Phase 11 — Diagnostic probes",
        "Phase 12 · Debug export bundle",
        "Phase 13 · UI overlay and disabled-action explanations",
        "Phase 14 · Final debug contract seal",
    ],
    "docs/ANDROID_DEBUG_WORKBENCH_FINAL_SEAL.md": [
        "Debug Workbench Final Contract Seal",
        "Non-negotiable safety contracts",
        "Safe export mode must omit raw file paths",
        "zero test failures, zero warnings, and zero diagnostics errors",
    ],
}

FORBIDDEN = {
    "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt": [
        "Debug overlay bypasses deletion",
        "Debug probes execute cleanup",
        "export includes file contents",
    ],
    "android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt": [
        "FileInputStream(",
        "readBytes()",
    ],
}

missing: list[str] = []
for relative, needles in REQUIRED.items():
    path = ROOT / relative
    if not path.exists():
        missing.append(f"missing file: {relative}")
        continue
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            missing.append(f"missing {needle!r} in {relative}")

for relative, needles in FORBIDDEN.items():
    path = ROOT / relative
    if not path.exists():
        continue
    text = path.read_text()
    for needle in needles:
        if needle in text:
            missing.append(f"forbidden {needle!r} in {relative}")

if missing:
    print("Debug Workbench final contract audit failed:", file=sys.stderr)
    for item in missing:
        print(f"- {item}", file=sys.stderr)
    raise SystemExit(1)

print("Debug Workbench final contract audit passed.")
