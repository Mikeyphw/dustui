#!/usr/bin/env python3
from __future__ import annotations
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRE_REM03_BLOCKERS = sorted([
    "cpp_scanner_production_active",
    "generic_release_truth_duplicated",
    "legacy_destructive_executor_active",
    "legacy_navigation_not_proven_retired",
    "python_legacy_target_present",
    "room_domain_authority_active",
])
POST_REM03_BLOCKERS = sorted(x for x in PRE_REM03_BLOCKERS if x != "room_domain_authority_active")
POST_REM04_BLOCKERS = sorted(x for x in POST_REM03_BLOCKERS if x not in {"cpp_scanner_production_active", "legacy_destructive_executor_active"})

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
    blockers = sorted(status.get("blockers", []))
    folded = set(status.get("folded_in", []))
    rem03_folded = "rem03_android_domain_protocol_cutover" in folded
    rem04_folded = "rem04_privileged_cleanup_mutation_cutover_cpp_retirement" in folded
    rem05_folded = "rem05_legacy_surface_release_truth_retirement" in folded
    expected = [] if rem05_folded else POST_REM04_BLOCKERS if rem04_folded else (POST_REM03_BLOCKERS if rem03_folded else PRE_REM03_BLOCKERS)
    if blockers != expected:
        stage = 'zero post-REM-05' if rem05_folded else ('three post-REM-04' if rem04_folded else ('five post-REM-03' if rem03_folded else 'six pre-REM-03'))
        fail.append(f"expected {stage} remaining blockers, got {status.get('blockers', [])}")
    observed = status.get("observed", {})
    if observed.get("android_go_runtime_binding") != "production":
        fail.append("Android Go runtime binding is not production")
    if observed.get("go_sqlite_persistence") != "go-owned-sqlite":
        fail.append("Go SQLite persistence is not authoritative")
    if observed.get("go_authority") != "product-default-qualified":
        fail.append("Go authority is not product-default-qualified")
    if rem03_folded and observed.get("room_domain_authority") != "none":
        fail.append("REM-03 is folded in but Room remains an observed production authority")
    expected_status = "closed" if rem05_folded else "reopened_blocked"
    expected_truth = "closed" if rem05_folded else "reopened"
    if status.get("status") != expected_status or status.get("gate_d_truth") != expected_truth:
        fail.append(f"Gate D truth must be {expected_status}/{expected_truth}")
except Exception as exc:
    fail.append(f"invalid OV14 ledger: {exc}")

if fail:
    for item in fail:
        print("REM-02 reconciliation failure:", item, file=sys.stderr)
    raise SystemExit(1)
print("REM-02 reconciliation hotfix PASS (successor retirement state remains truthful)")
