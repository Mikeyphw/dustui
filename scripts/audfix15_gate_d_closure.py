#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TOPOLOGY = ROOT / "migration/qualification/v1/gate_d_topology.json"
REGISTRY = ROOT / "migration/qualification/v1/gate_d_evidence.json"
CONFIG = ROOT / ".devtool.toml"

HISTORICAL_NODES = {
    "historical_gate_a_exo": "historical_inventory",
    "historical_gate_b_exo_arm64": "historical_inventory",
    "historical_gate_c_exo": "historical_inventory",
}
ARTIFACT_NODES = {
    "android_debug_assemble": {"outputs": ["app/build/outputs/apk/debug"], "kind": "apk-output"},
    "android_release_assemble": {"outputs": ["app/build/outputs/apk/release"], "kind": "apk-output"},
    "install_launch_smoke": {"outputs": ["app/build/outputs/androidTest-results", "app/build/reports/androidTests"], "kind": "instrumentation-output"},
    "rooted_arm64_runtime": {"outputs": ["build/evidence/gate-d/rooted-arm64-runtime.json"], "kind": "evidence-json"},
    "runtime_apk_binding": {"outputs": ["build/evidence/gate-d/runtime-apk-binding.json"], "kind": "evidence-json"},
    "devtool_artifact_identity": {"outputs": ["build/evidence/gate-d/devtool-artifact-identity.json"], "kind": "evidence-json"},
    "production_reachability": {"outputs": ["build/evidence/gate-d/production-reachability.json"], "kind": "evidence-json"},
}
EXPECTED_SUFFIX = ["gate_d_evidence_finalize", "qualification_state"]
CONTRACT_SCHEMA = "dustui.aud-fix-15.gate-d-closure-contract.v2"

GO_TEST_TMPDIR_COMMAND = 'mkdir -p .devtool/tmp/go-test && TMPDIR="$PWD/.devtool/tmp/go-test" go test ./...'
HISTORICAL_GO_TARGETS = ["dustui_gate_a_history", "dustui_gate_b_history", "dustui_gate_c_history"]


def _load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _load_config() -> dict[str, Any]:
    return tomllib.loads(CONFIG.read_text(encoding="utf-8"))


def _target(config: dict[str, Any], name: str) -> dict[str, Any]:
    return config.get("targets", {}).get(name, {})


def _workflow(config: dict[str, Any], target: str, name: str) -> list[dict[str, Any]]:
    raw = _target(config, target).get("workflows", {}).get(name, [])
    return [item for item in raw if isinstance(item, dict)]


def _jobs(config: dict[str, Any], target: str) -> dict[str, Any]:
    return _target(config, target).get("jobs", {})


def _ids(items: list[dict[str, Any]]) -> list[str]:
    return [str(item.get("id")) for item in items]


def _by_id(items: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {str(item.get("id")): item for item in items if item.get("id")}


def _depends_on(item: dict[str, Any]) -> list[str]:
    return [str(x) for x in item.get("depends_on", [])]


def _has_path(values: list[Any], expected: str) -> bool:
    return any(str(value) == expected or str(value).endswith(expected) for value in values)


def _reachable(items: list[dict[str, Any]], source: str, target: str) -> bool:
    graph = {str(item.get("id")): _depends_on(item) for item in items if item.get("id")}
    seen: set[str] = set()
    stack = [target]
    while stack:
        current = stack.pop()
        if current in seen:
            continue
        seen.add(current)
        deps = graph.get(current, [])
        if source in deps:
            return True
        stack.extend(deps)
    return False


def build_report() -> dict[str, Any]:
    failures: list[str] = []
    warnings: list[str] = []
    topology = _load_json(TOPOLOGY)
    registry = _load_json(REGISTRY)
    config = _load_config()
    gate_d = _workflow(config, "dustui_android", "gate_d")
    gate_d_by_id = _by_id(gate_d)
    gate_d_ids = _ids(gate_d)
    jobs = _jobs(config, "dustui_android")
    required_nodes = [str(x) for x in topology.get("required_nodes", [])]
    registry_nodes = [str(node.get("id")) for node in registry.get("nodes", []) if isinstance(node, dict)]

    if not required_nodes:
        failures.append("Gate-D topology has no required_nodes")
    if set(required_nodes) != set(registry_nodes):
        failures.append("Gate-D registry and topology required node sets differ")
    for node_id in required_nodes:
        if node_id not in gate_d_by_id:
            failures.append(f"Gate-D workflow missing required topology node: {node_id}")
    if not gate_d_ids or gate_d_ids[0] != "historical_inventory":
        failures.append("Gate-D workflow must start by preparing historical_inventory")
    if gate_d_ids[-2:] != EXPECTED_SUFFIX:
        failures.append("Gate-D workflow must end with gate_d_evidence_finalize then qualification_state")
    finalizer = gate_d_by_id.get("gate_d_evidence_finalize", {})
    if _depends_on(finalizer) != ["production_reachability"]:
        failures.append("Gate-D finalizer must depend only on production_reachability")
    qualification = gate_d_by_id.get("qualification_state", {})
    if _depends_on(qualification) != ["gate_d_evidence_finalize"]:
        failures.append("qualification_state must depend only on gate_d_evidence_finalize in Gate-D")
    for node_id, dependency in HISTORICAL_NODES.items():
        item = gate_d_by_id.get(node_id, {})
        if dependency not in _depends_on(item):
            failures.append(f"{node_id} must depend on historical_inventory")
    if not all(_reachable(gate_d, node, "gate_d_evidence_finalize") for node in ARTIFACT_NODES):
        failures.append("Every material artifact node must be upstream of gate_d_evidence_finalize")
    for node_id, spec in ARTIFACT_NODES.items():
        item = gate_d_by_id.get(node_id, {})
        ref = str(item.get("ref") or "")
        if not ref:
            failures.append(f"{node_id} has no Devtool workflow ref")
            continue
        if ref.startswith("job:"):
            job_name = ref.split(":", 1)[1]
            job = jobs.get(job_name) or {}
            outputs = [str(x) for x in job.get("outputs", [])]
            for expected in spec["outputs"]:
                if not _has_path(outputs, expected):
                    failures.append(f"{node_id} job {job_name} does not declare output {expected}")
        else:
            warnings.append(f"{node_id} is satisfied by non-local ref {ref}; output evidence must be bound by finalizer")
    finalizer_job = jobs.get("gate_d_evidence_finalize") or {}
    command = finalizer_job.get("command") or []
    if command != ["python3", "scripts/audfix15_gate_d_evidence.py", "finalize"]:
        failures.append("gate_d_evidence_finalize must run scripts/audfix15_gate_d_evidence.py finalize")
    finalizer_inputs = [str(x) for x in finalizer_job.get("inputs", [])]
    for expected in [
        "scripts/audfix15_gate_d_evidence.py",
        "scripts/audfix15_gate_d_bridge.py",
        "scripts/audfix15_gate_d_closure.py",
        "build/evidence/aud-fix/14/historical_discovery.json",
        "build/evidence/gate-d",
    ]:
        if not _has_path(finalizer_inputs, expected):
            failures.append(f"gate_d_evidence_finalize inputs missing {expected}")
    closure_job = jobs.get("audfix15_gate_d_closure") or {}
    if closure_job.get("command") != ["python3", "scripts/audfix15_gate_d_closure.py", "--check"]:
        failures.append("audfix15_gate_d_closure job must run closure --check")

    current_go_job = _jobs(config, "dustui_go").get("go_test_all") or {}
    if current_go_job.get("command") != ["sh", "-lc", GO_TEST_TMPDIR_COMMAND]:
        failures.append("dustui_go.go_test_all must run with a repository-local TMPDIR to avoid Termux hardlink tmp failures")
    for historical_target in HISTORICAL_GO_TARGETS:
        go_job = _jobs(config, historical_target).get("go_test") or {}
        if go_job.get("command") != ["sh", "-lc", GO_TEST_TMPDIR_COMMAND]:
            failures.append(f"{historical_target}.go_test must run with a historical worktree-local TMPDIR")
    contract = _workflow(config, "dustui_android", "aud_fix_15_contract")
    contract_ids = _ids(contract)
    if "closure" not in contract_ids:
        failures.append("aud_fix_15_contract workflow must include closure node")
    elif _depends_on(_by_id(contract).get("closure", {})) != ["doctor"]:
        failures.append("aud_fix_15_contract closure node must depend on doctor")
    wrappers = config.get("wrapper", {}).get("commands", {})
    gate_cmd = wrappers.get("aud-fix-15", {})
    if gate_cmd.get("workflow") != "gate_d" or gate_cmd.get("target") != "dustui_android":
        failures.append("wrapper command aud-fix-15 must execute dustui_android#gate_d")
    return {
        "schema": CONTRACT_SCHEMA,
        "status": "pass" if not failures else "fail",
        "failures": failures,
        "warnings": warnings,
        "gate_d_node_count": len(gate_d_ids),
        "required_node_count": len(required_nodes),
        "artifact_nodes": sorted(ARTIFACT_NODES),
        "finalizer": {
            "workflow_node": gate_d_by_id.get("gate_d_evidence_finalize"),
            "job_outputs": finalizer_job.get("outputs", []),
        },
        "single_execution_command": ["./devtoolw", "aud-fix-15"],
        "closure_rule": "Gate D is qualified only by running the full Devtool gate_d workflow; overlay apply validates the contract but does not run Android/Go/device evidence jobs.",
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    report = build_report()
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(f"AUD-FIX-15 Gate-D closure contract: failures={len(report['failures'])}")
        for failure in report["failures"]:
            print(f"  - {failure}")
        if not report["failures"]:
            print("converged=true")
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
