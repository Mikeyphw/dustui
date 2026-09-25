#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
QUAL = ROOT / "migration/qualification/v1"
TOPOLOGY = QUAL / "gate_d_topology.json"
REGISTRY = QUAL / "gate_d_evidence.json"
SCRIPT = ROOT / "scripts/audfix15_gate_d_evidence.py"
LIFECYCLE = ROOT / "scripts/audfix15_lifecycle.py"


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def _toml_workflow_slice() -> str:
    body = text(ROOT / ".devtool.toml")
    start = body.index("[targets.dustui_android.workflows]")
    end = body.index("[targets.dustui_go.jobs.go_test_all]")
    return body[start:end]


def lifecycle_failures() -> list[str]:
    failures: list[str] = []
    body = text(ROOT / ".devtool.toml")
    workflow = _toml_workflow_slice()
    for token in [
        "[targets.dustui_android.jobs.gate_d_evidence_finalize]",
        "[targets.dustui_android.jobs.audfix15_lifecycle_plan]",
        "[targets.dustui_android.jobs.audfix15_evidence_lifecycle]",
        "scripts/audfix15_gate_d_evidence.py",
        "scripts/audfix15_lifecycle.py",
        "migration/qualification/v1/gate_d_bundle.json",
        "[wrapper.commands.aud-fix-15]",
        "workflow = \"gate_d\"",
    ]:
        if token not in body:
            failures.append(f"missing AUD-FIX-15 Devtool binding: {token}")
    if not re.search(r'id\s*=\s*"gate_d_evidence_finalize"[^\n]+depends_on\s*=\s*\["production_reachability"\]', workflow):
        failures.append("gate_d workflow must finalize canonical evidence after production_reachability")
    if not re.search(r'id\s*=\s*"qualification_state"[^\n]+depends_on\s*=\s*\["gate_d_evidence_finalize"\]', workflow):
        failures.append("gate_d workflow must run qualification_state after Gate-D evidence finalization")
    validate_match = re.search(r'validate\s*=\s*\[(.*?)\n\]', workflow, flags=re.S)
    validate_body = validate_match.group(1) if validate_match else ""
    if 'af15_lifecycle' in validate_body or 'audfix15_lifecycle_plan' in validate_body:
        failures.append("ordinary validate workflow must not own AUD-FIX-15 lifecycle; artifact manifest validation.tests owns it")
    if 'af15_contract' in validate_body or 'audfix15_evidence_lifecycle' in validate_body:
        failures.append("ordinary validate workflow must not own AUD-FIX-15 contract; artifact manifest validation.tests owns it")
    if 'validate = [' in workflow and 'ref = "target:dustui_android#gate_d"' in workflow:
        failures.append("ordinary validate must not silently run full Gate-D")
    if not re.search(r'id\s*=\s*"historical_inventory"[^\n]+ref\s*=\s*"job:historical_gate_inventory"', workflow):
        failures.append("gate_d workflow must prepare historical worktrees before historical replay targets")
    lifecycle_source = text(LIFECYCLE)
    for token in ["apply_validates_itself", "manual_post_apply_commands_required", "required-by-artifact-manifest", "do_not_add_no_validate", "artifact-manifest-validation-tests"]:
        if token not in lifecycle_source:
            failures.append(f"AUD-FIX-15 lifecycle script missing token: {token}")
    script = text(SCRIPT)
    for token in [
        "source_projection_sha256",
        "gate_d_bundle_sha256",
        "DEVTOOL_RUN_ID is required",
        "ARTIFACT_EVIDENCE",
        "runtime_apk_binding",
        "historical_gate_b_exo_arm64",
        "refresh_generated_state",
    ]:
        if token not in script:
            failures.append(f"AUD-FIX-15 evidence finalizer missing token: {token}")
    return failures


def registry_failures() -> list[str]:
    failures: list[str] = []
    topo = load(TOPOLOGY)
    reg = load(REGISTRY)
    required = list(topo.get("required_nodes", []))
    reg_nodes = {n.get("id"): n for n in reg.get("nodes", []) if isinstance(n, dict)}
    if not required:
        failures.append("Gate-D topology has no required nodes")
    if set(required) != set(reg_nodes):
        failures.append("Gate-D registry and topology node sets differ")
    if reg.get("schema") not in {"dustui.gate-d-evidence-registry.v2", "dustui.gate-d-evidence-registry.v3"}:
        failures.append("Gate-D registry schema is not recognized")
    for node_id in required:
        node = reg_nodes.get(node_id) or {}
        contract = node.get("binding_contract") or {}
        required_fields = set(contract.get("required") or [])
        if node.get("requires_artifact_binding") and "artifact_sha256" not in required_fields:
            failures.append(f"{node_id}: artifact binding missing artifact_sha256")
        if node.get("historical"):
            for field in ["historical_commit", "historical_tree", "replay_tree", "workflow", "devtool_version", "run_id", "log_sha256"]:
                if field not in required_fields:
                    failures.append(f"{node_id}: historical binding contract missing {field}")
        else:
            for field in ["source_tree_sha256", "devtool_run_id", "devtool_version"]:
                if field not in required_fields:
                    failures.append(f"{node_id}: current binding contract missing {field}")
    return failures


def finalize_fail_closed_failures() -> list[str]:
    failures: list[str] = []
    env = dict(os.environ)
    env.pop("DEVTOOL_RUN_ID", None)
    env.pop("DEVTOOL_RUN_UUID", None)
    cp = subprocess.run([sys.executable, str(SCRIPT), "finalize"], cwd=ROOT, text=True, capture_output=True, env=env)
    if cp.returncode == 0:
        failures.append("manual Gate-D finalization without DEVTOOL_RUN_ID unexpectedly succeeded")
    if "DEVTOOL_RUN_ID is required" not in (cp.stdout + cp.stderr):
        failures.append("manual Gate-D finalization did not explain required Devtool run context")
    diag = subprocess.run([sys.executable, str(SCRIPT), "diagnose"], cwd=ROOT, text=True, capture_output=True)
    if diag.returncode != 0:
        failures.append("Gate-D evidence diagnostic command failed")
    else:
        try:
            obj = json.loads(diag.stdout)
            if obj.get("schema") != "dustui.aud-fix-15.diagnostic.v1":
                failures.append("diagnostic schema mismatch")
            if not obj.get("source_projection_sha256"):
                failures.append("diagnostic omitted source projection hash")
        except Exception as exc:
            failures.append(f"diagnostic output is not JSON: {exc}")
    return failures


def run(section: str) -> int:
    checks = {
        "lifecycle": lifecycle_failures,
        "registry": registry_failures,
        "fail-closed": finalize_fail_closed_failures,
    }
    sections = list(checks) if section == "all" else [section]
    failures: list[str] = []
    for name in sections:
        got = checks[name]()
        print(f"AUD-FIX-15 {name}: failures={len(got)}")
        for failure in got:
            print(f"  - {failure}")
        failures.extend(got)
    if not failures:
        print("converged=true")
    return 1 if failures else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--section", choices=["lifecycle", "registry", "fail-closed", "all"], default="all")
    return run(ap.parse_args().section)


if __name__ == "__main__":
    raise SystemExit(main())
