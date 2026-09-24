#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]

TEST = ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/data/CleaningPerformanceContractTest.kt"
CONTRACT = ROOT / "migration/contracts/v1/ov13_cleaning_performance_termux_hotfix.md"
AUDIT = ROOT / "migration/audit/v1/ov13_cleaning_performance_termux_hotfix_audit.md"
STATUS = ROOT / "migration/ledger/ov13_cleaning_performance_termux_hotfix_status.json"
DEVTOOL = ROOT / ".devtool.toml"

failures: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

source = TEST.read_text(encoding="utf-8") if TEST.is_file() else ""
require(TEST.is_file(), "CleaningPerformanceContractTest.kt missing")
require("assertEquals(15_000L, CleaningIntelligenceQualification.MAX_LARGE_DATASET_DURATION_MILLIS)" in source,
        "production 15s qualification budget assertion missing")
require("largeDatasetUnitTestBudgetMillis()" in source,
        "test runner budget helper missing")
require("TERMUX_GATE_LARGE_DATASET_DURATION_MILLIS = 30_000L" in source,
        "Termux/constrained runner budget missing")
require('System.getenv("HOME")' in source and 'System.getenv("PREFIX")' in source,
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

# Devtool target-local jobs/workflows were unsupported before the wrapper EXO overlay.
# They are now supported and required by DustUI Gate D/E wrapper flows.  This validator
# must not reject them.  It only verifies that any present jobs/workflows are paired
# with wrapper schema=1 so stale unsupported-schema regressions are still caught.
if DEVTOOL.is_file():
    cfg = tomllib.loads(DEVTOOL.read_text(encoding="utf-8"))
    wrapper = cfg.get("wrapper", {})
    targets = cfg.get("targets", {})
    android = targets.get("dustui_android", {})
    has_exo_jobs = "jobs" in android or "workflows" in android
    if has_exo_jobs:
        require(wrapper.get("schema") == 1, "Devtool jobs/workflows require wrapper schema=1")
        commands = wrapper.get("commands", {})
        require("gate-d" in commands, "wrapper gate-d command missing while jobs/workflows are present")

if failures:
    for failure in failures:
        print(f"OV13 cleaning performance hotfix failure: {failure}", file=sys.stderr)
    raise SystemExit(1)

print("OV13 cleaning performance Termux hotfix contract PASS")
