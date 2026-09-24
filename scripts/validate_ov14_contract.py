#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

DESIRED = {
    "cpp_scanner_runtime": "none",
    "python_runtime": "none",
    "room_domain_authority": "none",
    "legacy_destructive_executor": "none",
    "legacy_navigation": "none",
    "go_authority": "product-default-qualified",
    "android_go_runtime_binding": "production",
    "go_sqlite_persistence": "go-owned-sqlite",
    "generic_release_truth": "devtool-only",
}


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()

    failures: list[str] = []
    contract = ROOT / "migration/contracts/v1/ov14_legacy_retirement.md"
    audit = ROOT / "migration/audit/v1/ov14_legacy_retirement_audit.md"
    status_path = ROOT / "migration/ledger/ov14_legacy_retirement_status.json"
    retirement_go = ROOT / "go/internal/retirement/retirement.go"
    retirement_test = ROOT / "go/internal/retirement/retirement_test.go"
    truth_validator = ROOT / "scripts/validate_ov14_retirement_truth_hotfix.py"
    perf_validator = ROOT / "scripts/validate_ov13_cleaning_performance_termux_hotfix.py"

    for path, label in [
        (contract, "OV14 retirement contract"),
        (audit, "OV14 audit note"),
        (status_path, "OV14 status JSON"),
        (retirement_go, "Go retirement status package"),
        (retirement_test, "Go retirement status test"),
        (truth_validator, "OV14 retirement truth validator"),
        (perf_validator, "OV13 performance validator"),
    ]:
        require(path.is_file(), f"{label} missing", failures)

    contract_text = read(contract)
    require("desired retirement state" in contract_text.lower(), "contract must distinguish desired retirement state", failures)
    require("Observed state" in contract_text, "contract must record observed state", failures)
    for key, value in DESIRED.items():
        require(f"{key} = {value}" in contract_text, f"contract missing desired {key}={value}", failures)

    go_text = read(retirement_go)
    for token in ["func Desired() Status", "func Observed() Status", "func Current() Status { return Observed() }"]:
        require(token in go_text, f"retirement truth model missing {token}", failures)

    if status_path.is_file():
        try:
            status = json.loads(read(status_path))
        except json.JSONDecodeError as exc:
            failures.append(f"OV14 status JSON invalid: {exc}")
            status = {}
        require(status.get("status") == "reopened_blocked", "OV14 must be reopened_blocked after source truth audit", failures)
        require(status.get("gate_d_truth") == "reopened", "Gate D truth must be reopened", failures)
        require(status.get("desired") == DESIRED, "desired retirement ledger mismatch", failures)
        require(bool(status.get("blockers")), "OV14 blockers must remain explicit until cutover", failures)
        require(status.get("ov15_status") == "blocked_until_real_android_go_cutover", "OV15 must be blocked behind real cutover", failures)

    cfg = tomllib.loads(read(ROOT / ".devtool.toml")) if (ROOT / ".devtool.toml").is_file() else {}
    wrapper = cfg.get("wrapper", {})
    require(wrapper.get("schema") == 1, "Devtool wrapper schema=1 missing", failures)
    android = cfg.get("targets", {}).get("dustui_android", {})
    workflows = android.get("workflows", {})
    require(workflows.get("cutover_audit") == ["job:retirement_audit"], "cutover_audit workflow missing", failures)
    require(workflows.get("gate_d", [None])[0] == "job:retirement_gate", "Gate D must begin with retirement_gate", failures)
    require(workflows.get("validate", [None])[0] == "job:retirement_gate", "validate must begin with retirement_gate", failures)

    if args.audit_loop and truth_validator.is_file():
        proc = subprocess.run(["python3", str(truth_validator)], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(proc.returncode == 0, "retirement truth audit does not match source: " + (proc.stderr or proc.stdout).strip(), failures)
        proc = subprocess.run(["python3", "scripts/validate_ov13_cleaning_performance_termux_hotfix.py"], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(proc.returncode == 0, "folded OV13 performance validator does not pass: " + (proc.stderr or proc.stdout).strip(), failures)

    if failures:
        for failure in failures:
            print(f"OV14 legacy retirement failure: {failure}", file=sys.stderr)
        return 1
    print("OV14 legacy retirement truth contract PASS (Gate D reopened; OV15 blocked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
