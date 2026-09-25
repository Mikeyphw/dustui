#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from audfix14_history import discover_all  # noqa:E402

QUAL = ROOT / "migration/qualification/v1"
HIST = QUAL / "historical_gate_evidence.json"
TOPO = QUAL / "gate_d_topology.json"
REG = QUAL / "gate_d_evidence.json"
R1 = QUAL / "authority_gate_r1_attempt.json"
HIST_IDS = {"historical_gate_a_exo", "historical_gate_b_exo_arm64", "historical_gate_c_exo"}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def topology_failures() -> list[str]:
    failures: list[str] = []
    cfg = tomllib.loads((ROOT / ".devtool.toml").read_text())
    topo = load(TOPO); reg = load(REG)
    required = list(topo.get("required_nodes", [])); required_set = set(required)
    topo_nodes = {n.get("id"): n for n in topo.get("nodes", []) if isinstance(n, dict)}
    reg_ids = {n.get("id") for n in reg.get("nodes", []) if isinstance(n, dict)}
    if not required: failures.append("topology required_nodes is empty")
    if required_set != set(topo_nodes): failures.append(f"topology node mismatch missing={sorted(required_set-set(topo_nodes))} extra={sorted(set(topo_nodes)-required_set)}")
    if not required_set.issubset(reg_ids): failures.append(f"registry missing topology nodes {sorted(required_set-reg_ids)}")
    aw = cfg.get("targets", {}).get("dustui_android", {}).get("workflows", {})
    for wf in ["gate_d_preflight", "gate_d", "gate_d_successor_validators"]:
        if wf not in aw: failures.append(f"Devtool workflow missing {wf}")
    gate = aw.get("gate_d", [])
    gate_ids = [x.get("id") for x in gate if isinstance(x, dict)]
    # AUD-FIX-14 planned the required Gate-D topology nodes. AUD-FIX-15 is
    # allowed to append the evidence finalizer and generated-state check after
    # those nodes, but it must not reorder, drop, or inject nodes inside the
    # required topology prefix.
    suffix = ["gate_d_evidence_finalize", "qualification_state"]
    allowed_shapes = [
        required,
        required + suffix,
        ["historical_inventory"] + required,
        ["historical_inventory"] + required + suffix,
    ]
    if gate_ids not in allowed_shapes:
        failures.append("gate_d workflow node IDs must preserve gate_d_topology.required_nodes in order, with only the AUD-FIX-15 historical-inventory prefix and finalizer/check suffix allowed")
    if gate_ids and gate_ids[0] == "historical_inventory":
        inv = next((x for x in gate if isinstance(x, dict) and x.get("id") == "historical_inventory"), {})
        if inv.get("ref") != "job:historical_gate_inventory" or inv.get("depends_on") != []:
            failures.append("gate_d historical_inventory prefix must be the root historical_gate_inventory job")
        for hist_id in ["historical_gate_a_exo", "historical_gate_b_exo_arm64", "historical_gate_c_exo"]:
            node = next((x for x in gate if isinstance(x, dict) and x.get("id") == hist_id), {})
            if node.get("depends_on") != ["historical_inventory"]:
                failures.append(f"{hist_id}: Gate-D historical replay must depend on historical_inventory")
    gate_refs = {x.get("id"): x.get("ref") for x in gate if isinstance(x, dict)}
    for node_id, spec in topo_nodes.items():
        if gate_refs.get(node_id) != spec.get("workflow_ref"):
            failures.append(f"{node_id}: workflow ref mismatch topology={spec.get('workflow_ref')} gate_d={gate_refs.get(node_id)}")
    pre = aw.get("gate_d_preflight", [])
    pre_nodes = {str(x.get("id")): x for x in pre if isinstance(x, dict)}
    if pre_nodes.get("topology", {}).get("depends_on") != ["history"]:
        failures.append("gate_d_preflight topology must depend on history so live-tree isolation completes before topology/truth checks")
    pre_refs = {str(x.get("ref")) for x in pre if isinstance(x, dict)}
    forbidden = {spec["workflow_ref"] for spec in topo_nodes.values()} & pre_refs
    if forbidden: failures.append(f"gate_d_preflight executes qualification nodes {sorted(forbidden)}")
    # AUD-FIX-14 is topology/preflight only; validate must not silently become Gate D until AUD-FIX-15.
    if aw.get("validate") == aw.get("gate_d"): failures.append("validate points to gate_d before AUD-FIX-15")
    # Gate A must be pinned to the actual OV03 Gate-A tree, not the immediately
    # following Termux hardlink-fixture hotfix. The archived Gate-A overlay
    # records the pre-hotfix scan_test.go payload hash; using it as an immutable
    # fingerprint disambiguates otherwise identical Gate-A candidates without
    # guessing from commit subjects or chronology.
    hist = load(HIST)
    gate_a = next((g for g in hist.get("gates", []) if g.get("gate") == "A"), {})
    gate_a_fps = {x.get("path"): x.get("sha256") for x in gate_a.get("fingerprints", []) if isinstance(x, dict)}
    expected_gate_a_scan_test = "de239d170aaf76f9f198ae9bcbe479d7902c056de9e10b63d00e16e32475cd6a"
    if gate_a_fps.get("go/internal/scan/scan_test.go") != expected_gate_a_scan_test:
        failures.append("Gate A historical inventory must fingerprint archived pre-hotfix go/internal/scan/scan_test.go")

    for target in ["dustui_gate_a_history", "dustui_gate_b_history", "dustui_gate_c_history"]:
        t = cfg.get("targets", {}).get(target, {})
        if t.get("type") != "command" or "requalify" not in t.get("workflows", {}):
            failures.append(f"historical target incomplete: {target}")
    # The history preparation job invokes `git worktree add/remove`, which mutates
    # Git metadata in addition to .devtool/historical. Devtool forbids reserving
    # .git explicitly, and advertising only the JSON report as an output would
    # understate the write-set. Keep the job deliberately unbounded so the
    # scheduler takes an exclusive repository reservation for this first wave.
    hist_job = cfg.get("targets", {}).get("dustui_android", {}).get("jobs", {}).get("historical_gate_inventory", {})
    if hist_job.get("inputs") or hist_job.get("outputs"):
        failures.append("historical_gate_inventory must leave inputs/outputs undeclared for exclusive Git-metadata-safe scheduling")

    history_source = (ROOT / "scripts/audfix14_history.py").read_text(encoding="utf-8", errors="replace")
    required_isolation_tokens = [
        "_tracked_legacy_python",
        "removed-preexisting-untracked-exact-historical-copy",
        "removed-preexisting-untracked-exact-retired-oracle-copy-with-generated-cache",
        "EXPECTED_ORACLE_SOURCE_SHA256",
        "FAILED_PREEXISTING_LEGACY_PYTHON_UNPROVEN",
        "final_matches_expected",
    ]
    for token in required_isolation_tokens:
        if token not in history_source:
            failures.append(f"AUD-FIX-14 historical isolation missing preexisting-residue guard token: {token}")
    oracle = ROOT / "migration/oracle/python_textual/src/dust_tui"
    oracle_files = {
        p.relative_to(oracle).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in oracle.rglob("*") if p.is_file() and not p.is_symlink()
    } if oracle.is_dir() else {}
    h = hashlib.sha256()
    for rel, digest in sorted(oracle_files.items()):
        h.update(f"F\0{rel}\0{digest}\n".encode())
    if h.hexdigest() != "3255c9fad5be02656c5efdc660f433f2b363b587109a2713b556530d370b1a8e":
        failures.append("retired Python/Textual oracle source projection drifted from retained authoritative snapshots")
    return failures


def identity_failures() -> list[str]:
    failures=[]; topo=load(TOPO); reg=load(REG)
    regmap={n.get("id"):n for n in reg.get("nodes",[]) if isinstance(n,dict)}
    for n in topo.get("nodes",[]):
        nid=n.get("id"); req=set((n.get("binding_contract") or {}).get("required",[]))
        if n.get("historical"):
            if n.get("same_gate_d_tree"): failures.append(f"{nid}: historical node incorrectly requires current Gate-D tree")
            for field in ["historical_commit","historical_tree","replay_tree","workflow","devtool_version","run_id","log_sha256"]:
                if field not in req: failures.append(f"{nid}: historical binding missing {field}")
            if nid=="historical_gate_b_exo_arm64" and not {"device_arch","device_evidence_sha256"}.issubset(req): failures.append(f"{nid}: ARM64 device binding incomplete")
        else:
            if not n.get("same_gate_d_tree"): failures.append(f"{nid}: current node must bind to one Gate-D tree")
            for field in ["source_tree_sha256","devtool_run_id","devtool_version"]:
                if field not in req: failures.append(f"{nid}: current binding missing {field}")
            if n.get("requires_artifact_binding") and "artifact_sha256" not in req: failures.append(f"{nid}: artifact hash binding missing")
        r=regmap.get(nid,{})
        if r.get("workflow_ref") != n.get("workflow_ref"): failures.append(f"{nid}: registry/topology workflow_ref drift")
        if r.get("binding_contract") != n.get("binding_contract"): failures.append(f"{nid}: registry/topology binding contract drift")
    return failures


def successor_failures() -> list[str]:
    failures=[]
    for rel in ["migration/acceptance/v1/validator_lifecycle.json","scripts/validate_audfix13_contract.py","scripts/validate_ov13_contract.py","scripts/validate_ov14_contract.py"]:
        if not (ROOT/rel).is_file(): failures.append(f"successor authority missing {rel}")
    return failures


def r1_failures() -> list[str]:
    failures=[]
    if not R1.is_file(): return ["Authority Gate R1 failed-attempt record missing"]
    obj=load(R1)
    if obj.get("gate_r1_passed") is not False or obj.get("status") != "failed": failures.append("R1 attempt must remain failed despite later force-apply")
    if obj.get("post_failure_force_apply") is not True or obj.get("force_apply_validation") != "not-run": failures.append("R1 force-apply truth missing")
    app=(ROOT/"android/app/build.gradle.kts").read_text(errors="replace")
    if 'jniLibs.srcDir(layout.buildDirectory.dir(' in app: failures.append("Gradle Provider-backed jniLibs srcDir blocker still present")
    if 'jniLibs.directories.add(layout.buildDirectory.dir("generated/goRuntime/jniLibs").get().asFile)' not in app: failures.append("Gradle 9.7 source-set directories fix missing")
    return failures


def run(section: str, require_recoverable: bool) -> int:
    if section=="historical":
        report=discover_all(); print(json.dumps(report,indent=2))
        return 1 if require_recoverable and report["blockers"] else 0
    fn={"topology":topology_failures,"identity":identity_failures,"successors":successor_failures,"r1":r1_failures}[section]
    failures=fn(); print(f"AUD-FIX-14 {section}: failures={len(failures)}")
    for x in failures: print(f"  - {x}")
    return 1 if failures else 0


def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument("--section",choices=["historical","topology","identity","successors","r1","all"],default="all"); ap.add_argument("--require-recoverable",action="store_true"); ap.add_argument("--audit-loop",action="store_true")
    a=ap.parse_args(); sections=["historical","topology","identity","successors","r1"] if a.section=="all" else [a.section]
    for i in range(2 if a.audit_loop else 1):
        print(f"AUD-FIX-14 pass {i+1}")
        for s in sections:
            if run(s,a.require_recoverable): return 1
    print("converged=true"); return 0
if __name__=="__main__": raise SystemExit(main())
