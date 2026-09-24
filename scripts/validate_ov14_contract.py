#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_STATUS = {
    "cpp_scanner_runtime": "none",
    "python_runtime": "none",
    "room_domain_authority": "none",
    "legacy_destructive_executor": "none",
    "legacy_navigation": "none",
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
    perf_validator = ROOT / "scripts/validate_ov13_cleaning_performance_termux_hotfix.py"

    require(contract.is_file(), "OV14 retirement contract missing", failures)
    require(audit.is_file(), "OV14 audit note missing", failures)
    require(status_path.is_file(), "OV14 status JSON missing", failures)
    require(retirement_go.is_file(), "Go retirement status package missing", failures)
    require(retirement_test.is_file(), "Go retirement status test missing", failures)
    require(perf_validator.is_file(), "OV13 performance validator missing", failures)

    contract_text = read(contract)
    for key, value in REQUIRED_STATUS.items():
        require(f"{key} = {value}" in contract_text, f"contract missing {key}={value}", failures)

    go_text = read(retirement_go)
    for field in ["CppScannerRuntime", "PythonRuntime", "RoomDomainAuthority", "LegacyDestructiveExecutor", "LegacyNavigation"]:
        require(field in go_text, f"retirement status missing {field}", failures)
    require('GoAuthority:               "product-default"' in go_text or 'GoAuthority:' in go_text, "Go product authority missing", failures)

    perf_text = read(perf_validator)
    require("unsupported Devtool jobs schema must not be added" not in perf_text,
            "stale unsupported jobs rejection still present in OV13 performance validator", failures)
    require("unsupported Devtool workflows schema must not be added" not in perf_text,
            "stale unsupported workflows rejection still present in OV13 performance validator", failures)
    require('wrapper.get("schema") == 1' in perf_text,
            "OV13 performance validator must accept supported wrapper jobs only with wrapper schema=1", failures)

    if (ROOT / ".devtool.toml").is_file():
        cfg = tomllib.loads(read(ROOT / ".devtool.toml"))
        wrapper = cfg.get("wrapper", {})
        android = cfg.get("targets", {}).get("dustui_android", {})
        require(wrapper.get("schema") == 1, "Devtool wrapper schema=1 missing", failures)
        require("jobs" in android, "dustui_android target-local jobs missing", failures)
        require("workflows" in android, "dustui_android workflows missing", failures)
        require("gate_d" in android.get("workflows", {}), "Gate D workflow missing", failures)

    if status_path.is_file():
        try:
            status = json.loads(read(status_path))
        except json.JSONDecodeError as exc:
            failures.append(f"OV14 status JSON invalid: {exc}")
            status = {}
        for key, value in REQUIRED_STATUS.items():
            require(status.get(key) == value, f"status JSON {key} must be {value}", failures)
        require(status.get("folded_in"), "status JSON must record folded-in validator fix", failures)

    if args.audit_loop:
        proc = subprocess.run(["python3", "scripts/validate_ov13_cleaning_performance_termux_hotfix.py"], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        require(proc.returncode == 0, "folded OV13 performance validator does not pass: " + (proc.stderr or proc.stdout).strip(), failures)

    if failures:
        for failure in failures:
            print(f"OV14 legacy retirement failure: {failure}", file=sys.stderr)
        return 1
    print("OV14 legacy retirement contract PASS")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
