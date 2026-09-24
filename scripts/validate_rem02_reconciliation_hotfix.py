#!/usr/bin/env python3
from __future__ import annotations
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_BLOCKERS = sorted([
    "cpp_scanner_production_active",
    "generic_release_truth_duplicated",
    "legacy_destructive_executor_active",
    "legacy_navigation_not_proven_retired",
    "python_legacy_target_present",
    "room_domain_authority_active",
])

fail: list[str] = []
for validator in [
    "scripts/validate_rem01_android_go_runtime_binding.py",
    "scripts/validate_rem02_sqlite_integrity_hotfix.py",
    "scripts/validate_rem02_sqlite_persistence_authority.py",
    "scripts/validate_ov14_retirement_truth_hotfix.py",
]:
    proc = subprocess.run([sys.executable, validator], cwd=ROOT, text=True, capture_output=True)
    if proc.returncode:
        fail.append(f"{validator} failed: {(proc.stderr or proc.stdout).strip()}")

try:
    status = json.loads((ROOT / "migration/ledger/ov14_legacy_retirement_status.json").read_text())
    if sorted(status.get("blockers", [])) != EXPECTED_BLOCKERS:
        fail.append(f"expected six remaining blockers, got {status.get('blockers', [])}")
    observed = status.get("observed", {})
    if observed.get("android_go_runtime_binding") != "production":
        fail.append("Android Go runtime binding is not production")
    if observed.get("go_sqlite_persistence") != "go-owned-sqlite":
        fail.append("Go SQLite persistence is not authoritative")
    if observed.get("go_authority") != "product-default-qualified":
        fail.append("Go authority is not product-default-qualified")
    if status.get("status") != "reopened_blocked" or status.get("gate_d_truth") != "reopened":
        fail.append("Gate D truth must remain reopened_blocked")
except Exception as exc:
    fail.append(f"invalid OV14 ledger: {exc}")

if fail:
    for item in fail:
        print("REM-02 reconciliation failure:", item, file=sys.stderr)
    raise SystemExit(1)
print("REM-02 reconciliation hotfix PASS (six retirement blockers remain; REM-03 is next)")
