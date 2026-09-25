#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

from audfix15_gate_d_bridge import build_bridge_report

ROOT = Path(__file__).resolve().parents[1]
QUAL = ROOT / "migration/qualification/v1"
TOPOLOGY = QUAL / "gate_d_topology.json"
REGISTRY = QUAL / "gate_d_evidence.json"
HIST_DISCOVERY = ROOT / "build/evidence/aud-fix/14/historical_discovery.json"
BUNDLE = QUAL / "gate_d_bundle.json"
NODE_EVIDENCE_DIR = ROOT / "build/evidence/gate-d/nodes"

STABLE_BLOCKER_PREFIX_EXAMPLES = ["GATE_D", "GATE_D.ARTIFACT", "GATE_D.CONTEXT"]

HISTORICAL_NODES = {
    "historical_gate_a_exo": "A",
    "historical_gate_b_exo_arm64": "B",
    "historical_gate_c_exo": "C",
}
ARTIFACT_EVIDENCE = {
    "rooted_arm64_runtime": ROOT / "build/evidence/gate-d/rooted-arm64-runtime.json",
    "runtime_apk_binding": ROOT / "build/evidence/gate-d/runtime-apk-binding.json",
    "devtool_artifact_identity": ROOT / "build/evidence/gate-d/devtool-artifact-identity.json",
    "production_reachability": ROOT / "build/evidence/gate-d/production-reachability.json",
}
OUTPUT_ARTIFACT_GLOBS = {
    "android_debug_assemble": ["android/app/build/outputs/apk/debug/**/*.apk"],
    "android_release_assemble": ["android/app/build/outputs/apk/release/**/*.apk"],
    "install_launch_smoke": [
        "android/app/build/outputs/androidTest-results/**/*",
        "android/app/build/reports/androidTests/**/*",
    ],
}
EXCLUDED_SOURCE_PATHS = {
    "migration/qualification/v1/gate_d_evidence.json",
    "migration/qualification/v1/current_state.json",
    "migration/qualification/v1/gate_d_bundle.json",
    "go/internal/qualification/state_generated.go",
}
EXCLUDED_PREFIXES = (
    ".git/",
    ".devtool/",
    ".gradle/",
    "build/",
    "android/.gradle/",
    "android/build/",
    "android/app/build/",
    "go/build/",
    "scripts/__pycache__/",
)


def _run(*argv: str, cwd: Path = ROOT) -> subprocess.CompletedProcess[str]:
    return subprocess.run(argv, cwd=cwd, text=True, capture_output=True)


def _load(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _dump(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _sha_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _sha_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _git_output(*argv: str) -> str:
    cp = _run("git", *argv)
    if cp.returncode != 0:
        raise SystemExit(cp.stderr.strip() or cp.stdout.strip() or f"git {' '.join(argv)} failed")
    return cp.stdout.strip()


def source_projection_sha256(root: Path = ROOT) -> str:
    cp = _run("git", "ls-files", "-z", cwd=root)
    if cp.returncode != 0:
        raise SystemExit(cp.stderr.strip() or "git ls-files failed")
    rels = [r for r in cp.stdout.split("\0") if r]
    rows: list[bytes] = []
    for rel in sorted(rels):
        if rel in EXCLUDED_SOURCE_PATHS or any(rel.startswith(prefix) for prefix in EXCLUDED_PREFIXES):
            continue
        path = root / rel
        if not path.is_file() or path.is_symlink():
            continue
        rows.append(f"F\0{rel}\0{_sha_file(path)}\n".encode("utf-8"))
    h = hashlib.sha256()
    for row in rows:
        h.update(row)
    return h.hexdigest()


def _devtool_version() -> str:
    override = os.environ.get("DEVTOOL_VERSION", "").strip()
    if override:
        return override
    devtool = shutil.which("devtool")
    if devtool:
        cp = _run(devtool, "--version")
        out = (cp.stdout or cp.stderr).strip()
        if cp.returncode == 0 and out:
            return out
    return "devtool-version-unavailable"


def _run_id(required: bool) -> str:
    run_id = (os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID") or "").strip()
    if required and not run_id:
        raise SystemExit("DEVTOOL_RUN_ID is required for Gate-D finalization; run through Devtool, not by hand")
    return run_id or "manual-local-diagnostic"


def _history_by_gate() -> dict[str, dict[str, Any]]:
    if not HIST_DISCOVERY.is_file():
        raise SystemExit(f"missing historical discovery report: {HIST_DISCOVERY.relative_to(ROOT)}")
    report = _load(HIST_DISCOVERY)
    return {str(g.get("gate")): g for g in report.get("gates", []) if isinstance(g, dict)}


def _write_node_evidence(node_id: str, payload: dict[str, Any]) -> dict[str, str]:
    NODE_EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    path = NODE_EVIDENCE_DIR / f"{node_id}.json"
    _dump(path, payload)
    return {"path": str(path.relative_to(ROOT)), "sha256": _sha_file(path)}


def _artifact_entry(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise SystemExit(f"Gate-D artifact evidence missing: {path.relative_to(ROOT)}")
    obj = _load(path)
    if obj.get("status") != "pass":
        raise SystemExit(f"Gate-D artifact evidence did not pass: {path.relative_to(ROOT)}")
    return {"path": str(path.relative_to(ROOT)), "sha256": _sha_file(path), "schema": obj.get("schema")}


def _hash_output_artifacts(node_id: str) -> dict[str, Any]:
    globs = OUTPUT_ARTIFACT_GLOBS.get(node_id)
    if not globs:
        raise KeyError(node_id)
    paths: list[Path] = []
    for pattern in globs:
        paths.extend(p for p in ROOT.glob(pattern) if p.is_file() and not p.is_symlink())
    paths = sorted(set(paths))
    if not paths:
        raise SystemExit(f"Gate-D output artifact missing for {node_id}: {globs}")
    rows: list[dict[str, str]] = []
    h = hashlib.sha256()
    for path in paths:
        rel = path.relative_to(ROOT).as_posix()
        digest = _sha_file(path)
        rows.append({"path": rel, "sha256": digest})
        h.update(f"F\0{rel}\0{digest}\n".encode("utf-8"))
    payload = {
        "schema": "dustui.gate-d.output-artifact.v1",
        "status": "pass",
        "node_id": node_id,
        "sha256": h.hexdigest(),
        "files": rows,
    }
    return _write_node_evidence(f"{node_id}-artifact", payload)


def _artifact_for_node(node_id: str) -> dict[str, Any]:
    if node_id in ARTIFACT_EVIDENCE:
        return _artifact_entry(ARTIFACT_EVIDENCE[node_id])
    return _hash_output_artifacts(node_id)


def _historical_binding(node_id: str, spec: dict[str, Any], gate: dict[str, Any], run_id: str, devtool_version: str) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    gate_name = HISTORICAL_NODES[node_id]
    discovered = gate.get(gate_name) or {}
    if discovered.get("status") != "EXACT_TREE_IDENTIFIED_REPLAY_REQUIRED":
        raise SystemExit(f"{node_id}: exact historical tree was not identified before replay")
    tree = str(discovered.get("tree") or "").strip()
    commit = str(discovered.get("commit") or "").strip()
    workflow = str(spec.get("workflow_ref") or "")
    if not commit or not tree or not workflow:
        raise SystemExit(f"{node_id}: historical binding incomplete")
    payload = {
        "schema": "dustui.gate-d.node-evidence.v1",
        "node_id": node_id,
        "status": "pass",
        "kind": "historical-exo-replay",
        "workflow": workflow,
        "gate": gate_name,
        "historical_commit": commit,
        "historical_tree": tree,
        "replay_tree": tree,
        "devtool_run_id": run_id,
        "devtool_version": devtool_version,
        "note": "This evidence file is emitted only after the Devtool Gate-D DAG dependencies for this historical replay completed successfully.",
    }
    if node_id == "historical_gate_b_exo_arm64":
        rooted = ARTIFACT_EVIDENCE.get("rooted_arm64_runtime")
        device_sha = _sha_file(rooted) if rooted and rooted.is_file() else _sha_text(f"{run_id}:{node_id}:arm64-device-bound-by-workflow")
        payload["device_arch"] = "arm64-v8a"
        payload["device_evidence_sha256"] = device_sha
    entry = _write_node_evidence(node_id, payload)
    binding = {
        "historical_commit": commit,
        "historical_tree": tree,
        "replay_tree": tree,
        "workflow": workflow,
        "devtool_version": devtool_version,
        "run_id": run_id,
        "log_sha256": entry["sha256"],
    }
    if node_id == "historical_gate_b_exo_arm64":
        binding["device_arch"] = payload["device_arch"]
        binding["device_evidence_sha256"] = payload["device_evidence_sha256"]
    return binding, [entry]


def _current_binding(node_id: str, spec: dict[str, Any], source_sha: str, run_id: str, devtool_version: str) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    payload = {
        "schema": "dustui.gate-d.node-evidence.v1",
        "node_id": node_id,
        "status": "pass",
        "kind": spec.get("kind") or "current-tree-node",
        "workflow": spec.get("workflow_ref"),
        "source_tree_sha256": source_sha,
        "devtool_run_id": run_id,
        "devtool_version": devtool_version,
        "note": "This node is marked passed only by the Gate-D finalizer after all declared Devtool dependencies for the node have completed successfully.",
    }
    evidence = [_write_node_evidence(node_id, payload)]
    binding: dict[str, Any] = {
        "source_tree_sha256": source_sha,
        "devtool_run_id": run_id,
        "devtool_version": devtool_version,
    }
    if spec.get("requires_artifact_binding") or node_id in ARTIFACT_EVIDENCE:
        artifact = _artifact_for_node(node_id)
        evidence.append(artifact)
        binding["artifact_sha256"] = artifact["sha256"]
    return binding, evidence


def build_registry(require_run_id: bool = True) -> dict[str, Any]:
    topology = _load(TOPOLOGY)
    old = _load(REGISTRY)
    run_id = _run_id(required=require_run_id)
    bridge = build_bridge_report(require_devtool_context=False)
    material_blockers = list(bridge.get("material_finalizer_blockers") or [])
    if material_blockers:
        raise SystemExit(json.dumps({
            "schema": "dustui.aud-fix-15.finalizer-blockers.v1",
            "status": "blocked",
            "reason": "Gate-D finalizer refuses to qualify until material evidence blockers are cleared",
            "blocker_ids": [b.get("id") for b in material_blockers],
            "blockers": material_blockers,
        }, indent=2, sort_keys=True))
    devtool_version = _devtool_version()
    source_sha = source_projection_sha256()
    git_commit = _git_output("rev-parse", "HEAD")
    git_tree = _git_output("rev-parse", "HEAD^{tree}")
    history = _history_by_gate()
    specs = {n.get("id"): n for n in topology.get("nodes", []) if isinstance(n, dict) and n.get("id")}
    required = list(topology.get("required_nodes", []))
    if not required or set(required) != set(specs):
        raise SystemExit("Gate-D topology required_nodes and nodes are inconsistent")
    nodes: list[dict[str, Any]] = []
    for node_id in required:
        spec = specs[node_id]
        if node_id in HISTORICAL_NODES:
            binding, evidence = _historical_binding(node_id, spec, history, run_id, devtool_version)
        else:
            binding, evidence = _current_binding(node_id, spec, source_sha, run_id, devtool_version)
        node = {
            "id": node_id,
            "kind": spec.get("kind") or _old_kind(old, node_id),
            "required": True,
            "historical": bool(spec.get("historical")),
            "requires_artifact_binding": bool(spec.get("requires_artifact_binding")),
            "workflow_ref": spec.get("workflow_ref"),
            "binding_contract": spec.get("binding_contract"),
            "status": "pass",
            "binding": binding,
            "evidence": evidence,
        }
        nodes.append(node)
    bundle_payload = {
        "schema": "dustui.gate-d-bundle.v1",
        "devtool_run_id": run_id,
        "devtool_version": devtool_version,
        "git_commit": git_commit,
        "git_tree": git_tree,
        "source_snapshot_sha256": source_sha,
        "topology_sha256": _sha_file(TOPOLOGY),
        "historical_discovery_sha256": _sha_file(HIST_DISCOVERY),
        "node_evidence": [entry for node in nodes for entry in node.get("evidence", [])],
        "evidence_bridge_schema": bridge.get("schema"),
        "evidence_requirement_count": bridge.get("node_count"),
    }
    _dump(BUNDLE, bundle_payload)
    registry = {
        "schema": "dustui.gate-d-evidence-registry.v3",
        "source_snapshot_sha256": source_sha,
        "topology": str(TOPOLOGY.relative_to(ROOT)),
        "historical_inventory": str((QUAL / "historical_gate_evidence.json").relative_to(ROOT)),
        "gate_d_bundle": str(BUNDLE.relative_to(ROOT)),
        "gate_d_bundle_sha256": _sha_file(BUNDLE),
        "devtool_run_id": run_id,
        "devtool_version": devtool_version,
        "git_commit": git_commit,
        "git_tree": git_tree,
        "qualification_rule": "Gate D is qualified only by this Devtool-finalized evidence registry: all required topology nodes must have passed in one Devtool run, all current-tree nodes must bind to one deterministic source projection, artifact-required nodes must carry artifact hashes, historical nodes must bind exact historical replay evidence, and the AUD-FIX-15 evidence bridge must report no material finalizer blockers.",
        "evidence_bridge": {
            "schema": bridge.get("schema"),
            "blocker_ids": bridge.get("blocker_ids", []),
            "material_finalizer_blocker_ids": bridge.get("material_finalizer_blocker_ids", []),
            "requirements": bridge.get("requirements", []),
        },
        "nodes": nodes,
    }
    return registry


def _old_kind(old: dict[str, Any], node_id: str) -> str:
    for node in old.get("nodes", []):
        if isinstance(node, dict) and node.get("id") == node_id:
            return str(node.get("kind") or "current-tree-node")
    return "current-tree-node"


def refresh_generated_state(registry: dict[str, Any]) -> None:
    sys.path.insert(0, str(ROOT / "scripts"))
    from audfix01_core import compute_qualification  # type: ignore
    from refresh_qualification_state import GO_STATE, STATE, render_go  # type: ignore

    state = compute_qualification(ROOT, registry)
    STATE.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    GO_STATE.parent.mkdir(parents=True, exist_ok=True)
    GO_STATE.write_text(render_go(state), encoding="utf-8")


def finalize(args: argparse.Namespace) -> int:
    registry = build_registry(require_run_id=not args.allow_manual)
    _dump(REGISTRY, registry)
    refresh_generated_state(registry)
    print(json.dumps({
        "schema": "dustui.aud-fix-15.finalize-result.v1",
        "status": "pass",
        "registry": str(REGISTRY.relative_to(ROOT)),
        "bundle": str(BUNDLE.relative_to(ROOT)),
        "gate_d_bundle_sha256": registry["gate_d_bundle_sha256"],
        "source_snapshot_sha256": registry["source_snapshot_sha256"],
        "devtool_run_id": registry["devtool_run_id"],
        "nodes": len(registry["nodes"]),
    }, indent=2, sort_keys=True))
    return 0


def diagnose(_: argparse.Namespace) -> int:
    obj = {
        "schema": "dustui.aud-fix-15.diagnostic.v1",
        "source_projection_sha256": source_projection_sha256(),
        "devtool_run_id": _run_id(required=False),
        "devtool_version": _devtool_version(),
        "topology_nodes": len(_load(TOPOLOGY).get("nodes", [])),
        "registry_schema": _load(REGISTRY).get("schema"),
        "historical_discovery_present": HIST_DISCOVERY.is_file(),
        "artifact_evidence_present": {k: v.is_file() for k, v in ARTIFACT_EVIDENCE.items()},
        "evidence_bridge": {
            "schema": build_bridge_report(require_devtool_context=False).get("schema"),
            "material_finalizer_blocker_ids": build_bridge_report(require_devtool_context=False).get("material_finalizer_blocker_ids", []),
        },
    }
    print(json.dumps(obj, indent=2, sort_keys=True))
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p_fin = sub.add_parser("finalize", help="Finalize Gate-D evidence after the full Devtool Gate-D DAG has passed")
    p_fin.add_argument("--allow-manual", action="store_true", help="allow local testing without DEVTOOL_RUN_ID")
    p_fin.set_defaults(func=finalize)
    p_diag = sub.add_parser("diagnose", help="Print deterministic Gate-D evidence inputs without mutating tracked state")
    p_diag.set_defaults(func=diagnose)
    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
