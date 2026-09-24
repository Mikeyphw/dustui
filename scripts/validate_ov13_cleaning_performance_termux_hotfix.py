#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

TEST = ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/data/CleaningPerformanceContractTest.kt"
CONTRACT = ROOT / "migration/contracts/v1/ov13_cleaning_performance_termux_hotfix.md"
AUDIT = ROOT / "migration/audit/v1/ov13_cleaning_performance_termux_hotfix_audit.md"
STATUS = ROOT / "migration/ledger/ov13_cleaning_performance_termux_hotfix_status.json"

failures: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

source = TEST.read_text() if TEST.is_file() else ""
require(TEST.is_file(), "CleaningPerformanceContractTest.kt missing")
require("assertEquals(15_000L, CleaningIntelligenceQualification.MAX_LARGE_DATASET_DURATION_MILLIS)" in source,
        "production 15s qualification budget assertion missing")
require("largeDatasetUnitTestBudgetMillis()" in source,
        "test runner budget helper missing")
require("TERMUX_GATE_LARGE_DATASET_DURATION_MILLIS = 30_000L" in source,
        "Termux/constrained runner budget missing")
require("System.getenv(\"HOME\")" in source and "System.getenv(\"PREFIX\")" in source,
        "Termux environment detection missing")
require("Runtime.getRuntime().availableProcessors() <= 2" in source,
        "constrained CPU detection missing")
require("assertEquals(10_000, preview.exactPaths.size)" in source,
        "preview size assertion must remain")
require("assertEquals(10_000, growth.items.size)" in source,
        "growth size assertion must remain")
require(CONTRACT.is_file(), "contract note missing")
require(AUDIT.is_file(), "audit note missing")
require(STATUS.is_file(), "status JSON missing")
require("[targets.dustui_android.jobs" not in (ROOT / ".devtool.toml").read_text() if (ROOT / ".devtool.toml").is_file() else True,
        "unsupported Devtool jobs schema must not be added")
require("[targets.dustui_android.workflows" not in (ROOT / ".devtool.toml").read_text() if (ROOT / ".devtool.toml").is_file() else True,
        "unsupported Devtool workflows schema must not be added")

if failures:
    for failure in failures:
        print(f"OV13 cleaning performance hotfix failure: {failure}", file=sys.stderr)
    raise SystemExit(1)

print("OV13 cleaning performance Termux hotfix contract PASS")
