#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tomllib
from pathlib import Path
from typing import Any

from audfix15_gate_d_bridge import build_bridge_report

ROOT = Path(__file__).resolve().parents[1]
QUAL = ROOT / "migration/qualification/v1"
TOPOLOGY = QUAL / "gate_d_topology.json"
REGISTRY = QUAL / "gate_d_evidence.json"
STATE = QUAL / "current_state.json"
HIST_DISCOVERY = ROOT / "build/evidence/aud-fix/14/historical_discovery.json"
GATE_D_EVIDENCE = ROOT / "build/evidence/gate-d"

ARTIFACT_EVIDENCE = {
    "rooted_arm64_runtime": GATE_D_EVIDENCE / "rooted-arm64-runtime.json",
    "runtime_apk_binding": GATE_D_EVIDENCE / "runtime-apk-binding.json",
    "devtool_artifact_identity": GATE_D_EVIDENCE / "devtool-artifact-identity.json",
    "production_reachability": GATE_D_EVIDENCE / "production-reachability.json",
}
OUTPUT_ARTIFACTS = {
    "android_debug_assemble": ["android/app/build/outputs/apk/debug/**/*.apk"],
    "android_release_assemble": ["android/app/build/outputs/apk/release/**/*.apk"],
    "install_launch_smoke": [
        "android/app/build/outputs/androidTest-results/**/*",
        "android/app/build/reports/androidTests/**/*",
    ],
}
HISTORICAL_WORKTREES = {
    "historical_gate_a_exo": ROOT / ".devtool/historical/gate-a",
    "historical_gate_b_exo_arm64": ROOT / ".devtool/historical/gate-b",
    "historical_gate_c_exo": ROOT / ".devtool/historical/gate-c",
}
EXPECTED_GATE_D_PREFIX = ["historical_inventory", "historical_gate_a_exo", "historical_gate_b_exo_arm64", "historical_gate_c_exo"]
EXPECTED_FINAL_SUFFIX = ["gate_d_evidence_finalize", "qualification_state"]


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
        return obj if isinstance(obj, dict) else {}
    except Exception as exc:
        return {"_load_error": str(exc)}


def rel(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def command_exists(name: str) -> bool:
    return shutil.which(name) is not None


def git_clean() -> dict[str, Any]:
    cp = subprocess.run(["git", "status", "--short"], cwd=ROOT, text=True, capture_output=True)
    lines = [line for line in cp.stdout.splitlines() if line.strip()]
    return {"available": cp.returncode == 0, "dirty": bool(lines), "entries": lines[:80], "entry_count": len(lines)}


def workflow_ids(config: dict[str, Any], target: str, workflow: str) -> list[str]:
    try:
        items = config["targets"][target]["workflows"][workflow]
    except Exception:
        return []
    if isinstance(items, str):
        return [items]
    ids: list[str] = []
    for item in items if isinstance(items, list) else []:
        if isinstance(item, dict):
            ids.append(str(item.get("id") or item.get("ref") or ""))
        else:
            ids.append(str(item))
    return [i for i in ids if i]


def workflow_refs(config: dict[str, Any], target: str, workflow: str) -> dict[str, str]:
    try:
        items = config["targets"][target]["workflows"][workflow]
    except Exception:
        return {}
    refs: dict[str, str] = {}
    for item in items if isinstance(items, list) else []:
        if isinstance(item, dict) and item.get("id"):
            refs[str(item["id"])] = str(item.get("ref") or "")
    return refs


def workflow_diagnostics() -> dict[str, Any]:
    config = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    gate_d = workflow_ids(config, "dustui_android", "gate_d")
    contract = workflow_ids(config, "dustui_android", "aud_fix_15_contract")
    diagnostics = workflow_ids(config, "dustui_android", "aud_fix_15_diagnostics")
    refs = workflow_refs(config, "dustui_android", "gate_d")
    issues: list[str] = []
    if gate_d[:4] != EXPECTED_GATE_D_PREFIX:
        issues.append("gate_d workflow must begin with historical_inventory followed by Gate A/B/C replay nodes")
    if gate_d[-2:] != EXPECTED_FINAL_SUFFIX:
        issues.append("gate_d workflow must end with gate_d_evidence_finalize then qualification_state")
    if refs.get("gate_d_evidence_finalize") != "job:gate_d_evidence_finalize":
        issues.append("gate_d_evidence_finalize workflow node is not bound to the finalizer job")
    if "doctor" not in diagnostics:
        issues.append("aud_fix_15_diagnostics workflow missing doctor node")
    if not {"lifecycle", "doctor", "contract", "truth"}.issubset(set(contract)):
        issues.append("aud_fix_15_contract workflow must include lifecycle, doctor, contract, and truth nodes")
    return {"gate_d_ids": gate_d, "contract_ids": contract, "diagnostics_ids": diagnostics, "issues": issues, "ok": not issues}


def topology_diagnostics() -> dict[str, Any]:
    topo = load_json(TOPOLOGY)
    reg = load_json(REGISTRY)
    required = [str(x) for x in topo.get("required_nodes", [])]
    reg_nodes = {str(n.get("id")): n for n in reg.get("nodes", []) if isinstance(n, dict) and n.get("id")}
    issues: list[str] = []
    if not required:
        issues.append("Gate-D topology has no required nodes")
    if set(required) != set(reg_nodes):
        missing = sorted(set(required) - set(reg_nodes))
        extra = sorted(set(reg_nodes) - set(required))
        issues.append(f"registry/topology node mismatch missing={missing} extra={extra}")
    artifact_required = [n for n in required if (reg_nodes.get(n) or {}).get("requires_artifact_binding")]
    historical = [n for n in required if (reg_nodes.get(n) or {}).get("historical")]
    return {
        "schema": topo.get("schema"),
        "required_node_count": len(required),
        "historical_nodes": historical,
        "artifact_required_nodes": artifact_required,
        "issues": issues,
        "ok": not issues,
    }


def current_state_diagnostics() -> dict[str, Any]:
    state = load_json(STATE)
    gate_d = state.get("gate_d") if isinstance(state.get("gate_d"), dict) else {}
    return {
        "status": gate_d.get("status"),
        "qualified": bool(gate_d.get("qualified")),
        "reason": gate_d.get("reason"),
        "nonpassing_count": len(gate_d.get("nonpassing_nodes") or []),
        "historical_blockers": gate_d.get("historical_blockers") or [],
        "invalid_binding_nodes": gate_d.get("invalid_binding_nodes") or [],
        "missing_node_definitions": gate_d.get("missing_node_definitions") or [],
    }


def historical_diagnostics() -> dict[str, Any]:
    discovery = load_json(HIST_DISCOVERY)
    worktrees = {node: {"path": rel(path), "exists": path.is_dir()} for node, path in HISTORICAL_WORKTREES.items()}
    gates = discovery.get("gates") if isinstance(discovery.get("gates"), list) else []
    return {
        "discovery_path": rel(HIST_DISCOVERY),
        "discovery_present": HIST_DISCOVERY.is_file(),
        "gate_count": len(gates),
        "blocker_count": len(discovery.get("blockers") or []) if isinstance(discovery, dict) else 0,
        "prepared_worktrees": discovery.get("prepared_worktrees") or [],
        "worktrees": worktrees,
    }


def artifact_diagnostics() -> dict[str, Any]:
    evidence = {}
    missing = []
    for name, path in ARTIFACT_EVIDENCE.items():
        obj = load_json(path)
        status = obj.get("status") if obj else None
        present = path.is_file()
        evidence[name] = {"path": rel(path), "present": present, "status": status, "schema": obj.get("schema")}
        if not present:
            missing.append(name)
        elif status != "pass":
            missing.append(f"{name}:status={status}")
    outputs = {}
    for node, patterns in OUTPUT_ARTIFACTS.items():
        paths: list[str] = []
        for pattern in patterns:
            paths.extend(rel(p) for p in ROOT.glob(pattern) if p.is_file() and not p.is_symlink())
        outputs[node] = {"patterns": patterns, "count": len(paths), "sample": sorted(paths)[:10]}
    return {"leaf_evidence": evidence, "missing_or_nonpassing_leaf_evidence": missing, "output_artifacts": outputs}


def tool_diagnostics() -> dict[str, Any]:
    return {name: command_exists(name) for name in ["git", "python3", "go", "adb", "java"]}


def build_report() -> dict[str, Any]:
    bridge = build_bridge_report(require_devtool_context=False)
    workflow = workflow_diagnostics()
    topology = topology_diagnostics()
    state = current_state_diagnostics()
    structural_ok = workflow["ok"] and topology["ok"]
    report = {
        "schema": "dustui.aud-fix-15.gate-d-doctor.v2",
        "status": "ready-to-run-gate-d" if structural_ok else "structural-blocked",
        "gate_d_current_state": state,
        "workflow": workflow,
        "topology": topology,
        "historical": historical_diagnostics(),
        "artifacts": artifact_diagnostics(),
        "tools": tool_diagnostics(),
        "git": git_clean(),
        "bridge": bridge,
        "stable_blocker_ids": bridge.get("blocker_ids", []),
        "material_finalizer_blocker_ids": bridge.get("material_finalizer_blocker_ids", []),
        "evidence_requirements": bridge.get("requirements", []),
        "note": "This Gate-D doctor is diagnostic only. It emits stable blocker IDs and evidence requirements, but it must not mark Gate D qualified; only the complete Devtool gate_d workflow and finalizer may do that.",
    }
    if state.get("qualified"):
        report["status"] = "gate-d-qualified"
    elif bridge.get("material_finalizer_blockers"):
        report["status"] = "gate-d-material-evidence-blocked" if structural_ok else report["status"]
    return report

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="print machine-readable doctor report")
    ap.add_argument("--strict", action="store_true", help="fail if structural issues are found; Gate-D-open blockers are reported but not structural failures")
    args = ap.parse_args()
    report = build_report()
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(f"AUD-FIX-15 Gate-D doctor: {report['status']}")
        print(f"Gate D: {report['gate_d_current_state'].get('status')} reason={report['gate_d_current_state'].get('reason')}")
        print(f"Nonpassing nodes: {report['gate_d_current_state'].get('nonpassing_count')}")
        for issue in report["workflow"].get("issues", []) + report["topology"].get("issues", []):
            print(f"- {issue}")
    structural_ok = report["workflow"]["ok"] and report["topology"]["ok"]
    return 0 if structural_ok or not args.strict else 1


if __name__ == "__main__":
    raise SystemExit(main())
