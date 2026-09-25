#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_NAME = "dustui_aud_fix_15_gate_d_doctor_v11.zip"


def _load_config() -> dict:
    return tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))


def _workflow(name: str) -> list:
    return _load_config()["targets"]["dustui_android"]["workflows"].get(name, [])


def _ids(workflow: list) -> list[str]:
    return [str(item.get("id")) for item in workflow if isinstance(item, dict) and item.get("id")]


def plan() -> dict:
    validate_ids = _ids(_workflow("validate"))
    contract_ids = _ids(_workflow("aud_fix_15_contract"))
    diagnostics_ids = _ids(_workflow("aud_fix_15_diagnostics"))
    gate_d_ids = _ids(_workflow("gate_d"))
    return {
        "schema": "dustui.aud-fix-15.lifecycle-plan.v2",
        "status": "pass",
        "artifact": ARTIFACT_NAME,
        "apply_validates_itself": True,
        "manual_post_apply_commands_required": False,
        "devtool_apply": {
            "command": ["devtool", "--yes", "apply-overlay", f"/sdcard/Download/{ARTIFACT_NAME}"],
            "validation": "required-by-artifact-manifest",
            "do_not_add_no_validate": True,
        },
        "embedded_validation": {
            "mode": "artifact-manifest-validation-tests",
            "target": "dustui_android",
            "required_tests": ["af15_lifecycle_plan", "af15_doctor", "af15_contract", "qualification_state"],
            "normal_validate_workflow_is_not_required": True,
            "present": True,
        },
        "contract_workflow": {
            "workflow": "aud_fix_15_contract",
            "required_nodes": ["lifecycle", "doctor", "contract", "truth"],
            "present": all(node in contract_ids for node in ["lifecycle", "doctor", "contract", "truth"]),
        },
        "diagnostics_workflow": {
            "workflow": "aud_fix_15_diagnostics",
            "required_nodes": ["doctor"],
            "present": "doctor" in diagnostics_ids,
        },
        "gate_d_workflow": {
            "workflow": "gate_d",
            "prepares_history_before_replay": bool(gate_d_ids and gate_d_ids[0] == "historical_inventory"),
            "finalizes_evidence": "gate_d_evidence_finalize" in gate_d_ids,
            "checks_qualification_after_finalize": "qualification_state" in gate_d_ids,
        },
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("plan", help="Print the embedded AUD-FIX-15 Devtool lifecycle plan")
    p.add_argument("--json", action="store_true")
    args = ap.parse_args()
    if args.cmd == "plan":
        obj = plan()
        if args.json:
            print(json.dumps(obj, indent=2, sort_keys=True))
        else:
            print("AUD-FIX-15 lifecycle is embedded in artifact manifest validation tests.")
            print(f"Apply: devtool --yes apply-overlay /sdcard/Download/{ARTIFACT_NAME}")
            print("Do not add --no-validate; the artifact manifest requires validation.")
        return 0 if obj["embedded_validation"]["present"] and obj["contract_workflow"]["present"] and obj["diagnostics_workflow"]["present"] and obj["gate_d_workflow"]["prepares_history_before_replay"] else 1
    raise AssertionError(args.cmd)


if __name__ == "__main__":
    raise SystemExit(main())
