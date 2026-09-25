#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
QUAL = ROOT / "migration/qualification/v1"
TOPOLOGY = QUAL / "gate_d_topology.json"
REGISTRY = QUAL / "gate_d_evidence.json"
STATE = QUAL / "current_state.json"
HIST_DISCOVERY = ROOT / "build/evidence/aud-fix/14/historical_discovery.json"
GATE_D_EVIDENCE = ROOT / "build/evidence/gate-d"

STABLE_BLOCKER_PREFIX_EXAMPLES = ["GATE_D.ARTIFACT", "GATE_D.NODE", "GATE_D.HISTORY"]

HISTORICAL_NODES = {
    "historical_gate_a_exo": "A",
    "historical_gate_b_exo_arm64": "B",
    "historical_gate_c_exo": "C",
}
ARTIFACT_EVIDENCE = {
    "rooted_arm64_runtime": GATE_D_EVIDENCE / "rooted-arm64-runtime.json",
    "runtime_apk_binding": GATE_D_EVIDENCE / "runtime-apk-binding.json",
    "devtool_artifact_identity": GATE_D_EVIDENCE / "devtool-artifact-identity.json",
    "production_reachability": GATE_D_EVIDENCE / "production-reachability.json",
}
OUTPUT_ARTIFACT_GLOBS = {
    "android_debug_assemble": ["android/app/build/outputs/apk/debug/**/*.apk"],
    "android_release_assemble": ["android/app/build/outputs/apk/release/**/*.apk"],
    "install_launch_smoke": [
        "android/app/build/outputs/androidTest-results/**/*",
        "android/app/build/reports/androidTests/**/*",
    ],
}


def _load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
        return obj if isinstance(obj, dict) else {}
    except Exception as exc:
        return {"_load_error": str(exc)}


def _rel(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def _git_available() -> bool:
    return shutil.which("git") is not None and subprocess.run(["git", "rev-parse", "--is-inside-work-tree"], cwd=ROOT, text=True, capture_output=True).returncode == 0


def _workflow_items(config: dict[str, Any], target: str, workflow: str) -> list[dict[str, Any]]:
    try:
        raw = config["targets"][target]["workflows"][workflow]
    except Exception:
        return []
    if not isinstance(raw, list):
        return []
    out: list[dict[str, Any]] = []
    for item in raw:
        if isinstance(item, dict) and item.get("id"):
            out.append(item)
    return out


def _config() -> dict[str, Any]:
    try:
        return tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    except Exception:
        return {}


def _registry_nodes(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {str(n.get("id")): n for n in registry.get("nodes", []) if isinstance(n, dict) and n.get("id")}


def _topology_nodes(topology: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {str(n.get("id")): n for n in topology.get("nodes", []) if isinstance(n, dict) and n.get("id")}


def _status_for_node(node_id: str, registry_node: dict[str, Any]) -> str:
    return str(registry_node.get("status") or "missing-registry-node")


def _artifact_state(path: Path) -> dict[str, Any]:
    present = path.is_file()
    obj = _load_json(path)
    status = obj.get("status") if present else None
    return {
        "path": _rel(path),
        "present": present,
        "schema": obj.get("schema"),
        "status": status,
        "load_error": obj.get("_load_error"),
        "finalizer_ready": bool(present and status == "pass"),
    }


def _output_state(patterns: list[str]) -> dict[str, Any]:
    files: list[str] = []
    for pattern in patterns:
        files.extend(_rel(p) for p in ROOT.glob(pattern) if p.is_file() and not p.is_symlink())
    files = sorted(set(files))
    return {"patterns": patterns, "count": len(files), "sample": files[:12], "finalizer_ready": bool(files)}


def _blocker(blocker_id: str, *, node_id: str | None, category: str, severity: str, reason: str, satisfier: dict[str, Any] | None = None) -> dict[str, Any]:
    obj: dict[str, Any] = {
        "id": blocker_id,
        "category": category,
        "severity": severity,
        "reason": reason,
    }
    if node_id:
        obj["node_id"] = node_id
    if satisfier:
        obj["satisfier"] = satisfier
    return obj


def _node_blocker_id(prefix: str, node_id: str, suffix: str) -> str:
    return f"GATE_D.{prefix}.{node_id.upper().replace('-', '_')}.{suffix}"


def build_bridge_report(*, require_devtool_context: bool = False) -> dict[str, Any]:
    topology = _load_json(TOPOLOGY)
    registry = _load_json(REGISTRY)
    state = _load_json(STATE)
    history = _load_json(HIST_DISCOVERY)
    config = _config()
    gate_d_items = _workflow_items(config, "dustui_android", "gate_d")
    gate_d_by_id = {str(item.get("id")): item for item in gate_d_items}
    topology_map = _topology_nodes(topology)
    registry_map = _registry_nodes(registry)
    required = [str(x) for x in topology.get("required_nodes", [])]
    blockers: list[dict[str, Any]] = []
    material_blockers: list[dict[str, Any]] = []

    if not TOPOLOGY.is_file():
        b = _blocker("GATE_D.STRUCTURE.TOPOLOGY.MISSING", node_id=None, category="structure", severity="finalizer-blocking", reason="Gate-D topology file is missing")
        blockers.append(b); material_blockers.append(b)
    if not REGISTRY.is_file():
        b = _blocker("GATE_D.STRUCTURE.REGISTRY.MISSING", node_id=None, category="structure", severity="finalizer-blocking", reason="Gate-D evidence registry file is missing")
        blockers.append(b); material_blockers.append(b)
    if set(required) != set(topology_map):
        b = _blocker("GATE_D.STRUCTURE.TOPOLOGY.NODE_SET_MISMATCH", node_id=None, category="structure", severity="finalizer-blocking", reason="Gate-D topology required_nodes and nodes differ")
        blockers.append(b); material_blockers.append(b)
    if set(required) != set(registry_map):
        b = _blocker("GATE_D.STRUCTURE.REGISTRY.NODE_SET_MISMATCH", node_id=None, category="structure", severity="finalizer-blocking", reason="Gate-D registry nodes do not match topology required_nodes")
        blockers.append(b); material_blockers.append(b)
    if not _git_available():
        b = _blocker("GATE_D.CONTEXT.GIT.UNAVAILABLE", node_id=None, category="context", severity="finalizer-blocking", reason="Git metadata is unavailable for source/tree binding")
        blockers.append(b); material_blockers.append(b)
    run_id = (os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID") or "").strip()
    if require_devtool_context and not run_id:
        b = _blocker("GATE_D.CONTEXT.DEVTOOL_RUN_ID.MISSING", node_id=None, category="context", severity="finalizer-blocking", reason="DEVTOOL_RUN_ID is required for Gate-D finalization")
        blockers.append(b); material_blockers.append(b)

    gates = {str(g.get("gate")): g for g in history.get("gates", []) if isinstance(g, dict) and g.get("gate")}
    history_present = HIST_DISCOVERY.is_file()
    nodes: list[dict[str, Any]] = []
    for node_id in required:
        topo = topology_map.get(node_id) or {}
        reg = registry_map.get(node_id) or {}
        workflow_item = gate_d_by_id.get(node_id) or {}
        workflow_ref = str(topo.get("workflow_ref") or reg.get("workflow_ref") or workflow_item.get("ref") or "")
        satisfier: dict[str, Any] = {
            "workflow_ref": workflow_ref,
            "gate_d_workflow_node": node_id if node_id in gate_d_by_id else None,
            "depends_on": workflow_item.get("depends_on") if isinstance(workflow_item, dict) else [],
        }
        requirement: dict[str, Any] = {
            "node_id": node_id,
            "historical": bool(topo.get("historical") or reg.get("historical")),
            "requires_artifact_binding": bool(topo.get("requires_artifact_binding") or reg.get("requires_artifact_binding")),
            "binding_contract_required": list((topo.get("binding_contract") or reg.get("binding_contract") or {}).get("required") or []),
            "status": _status_for_node(node_id, reg),
            "satisfier": satisfier,
            "artifacts": [],
            "blocker_ids": [],
            "finalizer_blocker_ids": [],
        }
        if node_id not in gate_d_by_id:
            b = _blocker(_node_blocker_id("STRUCTURE", node_id, "WORKFLOW_NODE_MISSING"), node_id=node_id, category="structure", severity="finalizer-blocking", reason="Node is not present in the gate_d workflow", satisfier=satisfier)
            blockers.append(b); material_blockers.append(b)
            requirement["blocker_ids"].append(b["id"]); requirement["finalizer_blocker_ids"].append(b["id"])
        status = requirement["status"]
        if status != "pass":
            severity = "historical-replay-required" if node_id in HISTORICAL_NODES else "gate-d-execution-required"
            suffix = "REPLAY_REQUIRED" if node_id in HISTORICAL_NODES else "NOT_EXECUTED"
            b = _blocker(_node_blocker_id("NODE", node_id, suffix), node_id=node_id, category="execution", severity=severity, reason=f"Gate-D node is not passing yet: {status}", satisfier=satisfier)
            blockers.append(b)
            requirement["blocker_ids"].append(b["id"])
        if node_id in HISTORICAL_NODES:
            gate_name = HISTORICAL_NODES[node_id]
            gate = gates.get(gate_name) or {}
            requirement["artifacts"].append({"kind": "historical_discovery", "path": _rel(HIST_DISCOVERY), "present": history_present, "gate": gate_name, "status": gate.get("status")})
            if not history_present:
                b = _blocker(_node_blocker_id("HISTORY", node_id, "DISCOVERY_MISSING"), node_id=node_id, category="history", severity="finalizer-blocking", reason="Historical discovery report is missing", satisfier=satisfier)
                blockers.append(b); material_blockers.append(b); requirement["blocker_ids"].append(b["id"]); requirement["finalizer_blocker_ids"].append(b["id"])
            elif gate.get("status") != "EXACT_TREE_IDENTIFIED_REPLAY_REQUIRED":
                b = _blocker(_node_blocker_id("HISTORY", node_id, "EXACT_TREE_NOT_IDENTIFIED"), node_id=node_id, category="history", severity="finalizer-blocking", reason="Historical gate does not have an exact tree identity", satisfier=satisfier)
                blockers.append(b); material_blockers.append(b); requirement["blocker_ids"].append(b["id"]); requirement["finalizer_blocker_ids"].append(b["id"])
        if node_id in OUTPUT_ARTIFACT_GLOBS:
            art = _output_state(OUTPUT_ARTIFACT_GLOBS[node_id])
            art["kind"] = "output_glob"
            requirement["artifacts"].append(art)
            if not art["finalizer_ready"]:
                b = _blocker(_node_blocker_id("ARTIFACT", node_id, "OUTPUT_MISSING"), node_id=node_id, category="artifact", severity="finalizer-blocking", reason="Required output artifact files are missing", satisfier=satisfier)
                blockers.append(b); material_blockers.append(b); requirement["blocker_ids"].append(b["id"]); requirement["finalizer_blocker_ids"].append(b["id"])
        if node_id in ARTIFACT_EVIDENCE:
            art = _artifact_state(ARTIFACT_EVIDENCE[node_id])
            art["kind"] = "evidence_json"
            requirement["artifacts"].append(art)
            if not art["finalizer_ready"]:
                suffix = "MISSING" if not art["present"] else "NOT_PASSING"
                b = _blocker(_node_blocker_id("ARTIFACT", node_id, suffix), node_id=node_id, category="artifact", severity="finalizer-blocking", reason="Required evidence JSON is missing or not passing", satisfier=satisfier)
                blockers.append(b); material_blockers.append(b); requirement["blocker_ids"].append(b["id"]); requirement["finalizer_blocker_ids"].append(b["id"])
        nodes.append(requirement)

    gate_d_state = state.get("gate_d") if isinstance(state.get("gate_d"), dict) else {}
    report = {
        "schema": "dustui.aud-fix-15.gate-d-evidence-bridge.v1",
        "status": "finalizer-ready" if not material_blockers else "blocked",
        "gate_d_state": {
            "status": gate_d_state.get("status"),
            "qualified": bool(gate_d_state.get("qualified")),
            "reason": gate_d_state.get("reason"),
        },
        "required_nodes": required,
        "node_count": len(nodes),
        "blockers": blockers,
        "blocker_ids": [b["id"] for b in blockers],
        "material_finalizer_blockers": material_blockers,
        "material_finalizer_blocker_ids": [b["id"] for b in material_blockers],
        "requirements": nodes,
        "artifacts": {
            "historical_discovery": _rel(HIST_DISCOVERY),
            "gate_d_evidence_root": _rel(GATE_D_EVIDENCE),
            "output_globs": OUTPUT_ARTIFACT_GLOBS,
            "evidence_json": {k: _rel(v) for k, v in ARTIFACT_EVIDENCE.items()},
        },
        "finalizer_contract": {
            "consumed_by": "scripts/audfix15_gate_d_evidence.py",
            "requires_devtool_run_id": True,
            "refuses_material_blockers": True,
            "non_artifact_nodes_are_bound_by_devtool_dag_dependencies": True,
        },
    }
    return report


def assert_bridge_contract() -> list[str]:
    failures: list[str] = []
    report = build_bridge_report(require_devtool_context=False)
    if report.get("schema") != "dustui.aud-fix-15.gate-d-evidence-bridge.v1":
        failures.append("bridge schema mismatch")
    if not report.get("requirements"):
        failures.append("bridge emitted no node requirements")
    req_by_id = {r.get("node_id"): r for r in report.get("requirements", []) if isinstance(r, dict)}
    required = set(report.get("required_nodes") or [])
    if set(req_by_id) != required:
        failures.append("bridge requirements do not cover every required Gate-D node")
    for node_id, req in sorted(req_by_id.items()):
        sat = req.get("satisfier") or {}
        if not sat.get("workflow_ref"):
            failures.append(f"{node_id}: missing workflow_ref satisfier")
        if node_id in OUTPUT_ARTIFACT_GLOBS and not req.get("artifacts"):
            failures.append(f"{node_id}: missing output artifact mapping")
        if node_id in ARTIFACT_EVIDENCE and not req.get("artifacts"):
            failures.append(f"{node_id}: missing evidence JSON mapping")
        if node_id in HISTORICAL_NODES and not req.get("artifacts"):
            failures.append(f"{node_id}: missing historical discovery mapping")
    for blocker in report.get("blockers", []):
        bid = blocker.get("id")
        if not isinstance(bid, str) or not bid.startswith("GATE_D."):
            failures.append(f"unstable blocker id: {bid!r}")
        if not blocker.get("severity") or not blocker.get("category"):
            failures.append(f"blocker missing severity/category: {bid}")
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="print machine-readable bridge report")
    ap.add_argument("--check", action="store_true", help="validate bridge contract")
    ap.add_argument("--finalizer-context", action="store_true", help="include DEVTOOL_RUN_ID as a material blocker if absent")
    args = ap.parse_args()
    if args.check:
        failures = assert_bridge_contract()
        print(f"AUD-FIX-15 Gate-D evidence bridge: failures={len(failures)}")
        for failure in failures:
            print(f"  - {failure}")
        if not failures:
            print("converged=true")
        return 1 if failures else 0
    report = build_bridge_report(require_devtool_context=args.finalizer_context)
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(f"AUD-FIX-15 Gate-D evidence bridge: {report['status']}")
        print(f"blockers={len(report['blockers'])} material_finalizer_blockers={len(report['material_finalizer_blockers'])}")
        for blocker in report["blockers"][:30]:
            print(f"- {blocker['id']}: {blocker['reason']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
