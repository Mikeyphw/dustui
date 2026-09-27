#!/usr/bin/env python3
"""Gate-E qualification reuse/reconciliation.

This is deliberately qualification-specific, not a generic task cache. Devtool
still owns execution. DustUI owns the semantic rule that a Gate-E seal is bound
to the source projection, while commit/tree IDs are provenance. For a changed
projection, only previously executed *expensive* nodes whose curated source +
execution fingerprints are byte-for-byte unchanged may be composed into the
new seal; policy/qualification nodes are rerun on the successor projection.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

import audfix17_gate_e as gate
from audfix01_core import REQUIRED_GATE_E_NODES, load_json
from audfix16_retirement import source_projection_sha256
from audfix17_identity import (
    POLICY_REVALIDATION_NODES,
    REUSABLE_EXECUTION_NODES,
    compare_reusable_fingerprints,
    node_fingerprint,
)

ROOT = Path(__file__).resolve().parents[1]
QUAL = ROOT / "migration/qualification/v1"
EVIDENCE = QUAL / "gate_e_evidence.json"
BUNDLE = QUAL / "gate_e_bundle.json"
STATE = QUAL / "current_state.json"
EVIDENCE_REL = "migration/qualification/v1/gate_e_evidence.json"
BUNDLE_REL = "migration/qualification/v1/gate_e_bundle.json"


def run(*argv: str, timeout: int = 300, check: bool = False, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    cp = subprocess.run(
        list(argv), cwd=str(cwd or ROOT), text=True, capture_output=True,
        check=False, timeout=timeout,
    )
    if check and cp.returncode != 0:
        raise RuntimeError(
            f"command failed ({cp.returncode}): {' '.join(argv)}\n{cp.stdout}{cp.stderr}"
        )
    return cp


def git(*args: str, check: bool = True) -> str:
    cp = run("git", "-c", f"safe.directory={ROOT}", *args, timeout=60)
    if check and cp.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} failed: {cp.stderr.strip()}")
    return cp.stdout.strip()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def json_from_git(commit: str, rel: str) -> tuple[dict[str, Any] | None, bytes | None]:
    cp = subprocess.run(
        ["git", "-c", f"safe.directory={ROOT}", "show", f"{commit}:{rel}"], cwd=ROOT,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False, timeout=60,
    )
    if cp.returncode != 0:
        return None, None
    try:
        value = json.loads(cp.stdout)
    except Exception:
        return None, cp.stdout
    return (value if isinstance(value, dict) else None), cp.stdout


def registry_is_candidate(obj: dict[str, Any]) -> bool:
    return (
        obj.get("status") == "pass"
        and obj.get("schema") in {"dustui.gate-e.evidence-registry.v1", "dustui.gate-e.evidence-registry.v2"}
        and list(obj.get("required_nodes") or []) == list(REQUIRED_GATE_E_NODES)
        and all(
            isinstance(n, dict) and n.get("status") == "pass"
            for n in obj.get("nodes", [])
        )
    )


def find_candidate() -> dict[str, Any]:
    # Prefer current live evidence if present.
    if EVIDENCE.is_file():
        try:
            obj = load_json(EVIDENCE)
        except Exception:
            obj = {}
        if registry_is_candidate(obj):
            bundle = load_json(BUNDLE) if BUNDLE.is_file() else {}
            return {
                "evidence": obj,
                "bundle": bundle,
                "evidence_commit": git("rev-parse", "HEAD"),
                "evidence_sha256": hashlib.sha256(EVIDENCE.read_bytes()).hexdigest(),
                "source": "live",
            }

    # Evidence may have been intentionally removed by a successor source commit.
    # Search repository history rather than forcing the user to preserve a
    # generated evidence file in every source commit.
    cp = run("git", "-c", f"safe.directory={ROOT}", "log", "--format=%H", "--all", "--", EVIDENCE_REL, timeout=60)
    if cp.returncode != 0:
        raise RuntimeError("cannot search Git history for prior Gate-E evidence")
    seen: set[str] = set()
    for commit in [x.strip() for x in cp.stdout.splitlines() if x.strip()]:
        if commit in seen:
            continue
        seen.add(commit)
        obj, raw = json_from_git(commit, EVIDENCE_REL)
        if not obj or not raw or not registry_is_candidate(obj):
            continue
        bundle, _ = json_from_git(commit, BUNDLE_REL)
        return {
            "evidence": obj,
            "bundle": bundle or {},
            "evidence_commit": commit,
            "evidence_sha256": sha256_bytes(raw),
            "source": "git-history",
        }
    raise RuntimeError("no passing Gate-E evidence registry is available in the live tree or Git history")


def validated_head(registry: dict[str, Any]) -> str:
    for key in ("validated_at_git_head", "source_git_head"):
        value = str(registry.get(key) or "").strip()
        if value:
            return value
    raise RuntimeError("prior Gate-E evidence has no validation Git provenance")


def current_identity() -> dict[str, str]:
    dirty = git("status", "--porcelain")
    if dirty:
        raise RuntimeError("Gate-E reconcile requires a clean committed worktree")
    return {
        "git_head": git("rev-parse", "HEAD"),
        "git_tree": git("rev-parse", "HEAD^{tree}"),
        "source_projection_sha256": source_projection_sha256(),
    }


def node_map(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        str(n.get("id")): n
        for n in registry.get("nodes", [])
        if isinstance(n, dict) and n.get("id")
    }


def plan() -> dict[str, Any]:
    ident = current_identity()
    candidate = find_candidate()
    old = candidate["evidence"]
    old_projection = str(old.get("qualified_source_projection_sha256") or old.get("source_projection_sha256") or "")
    old_head = validated_head(old)
    if not old_projection:
        raise RuntimeError("prior Gate-E evidence has no source projection")
    if old_projection == ident["source_projection_sha256"]:
        return {
            "schema": "dustui.gate-e.reconcile-plan.v2",
            "status": "qualified-projection-unchanged",
            "current": ident,
            "prior": {
                "source_projection_sha256": old_projection,
                "validated_git_head": old_head,
                "evidence_commit": candidate["evidence_commit"],
                "schema": old.get("schema"),
            },
            "reusable_execution_nodes": list(REUSABLE_EXECUTION_NODES),
            "changed_execution_nodes": [],
            "policy_revalidation_nodes": [],
            "requires_canonical_release": False,
            "requires_full_gate_e": False,
        }
    fingerprints = compare_reusable_fingerprints(old_head, ident["git_head"])
    changed = list(fingerprints["changed_execution_nodes"])
    return {
        "schema": "dustui.gate-e.reconcile-plan.v2",
        "status": "policy-only-requalification-possible" if not changed else "material-execution-changed",
        "current": ident,
        "prior": {
            "source_projection_sha256": old_projection,
            "validated_git_head": old_head,
            "evidence_commit": candidate["evidence_commit"],
            "schema": old.get("schema"),
        },
        "reusable_execution_nodes": fingerprints["reusable_execution_nodes"],
        "changed_execution_nodes": changed,
        "policy_revalidation_nodes": list(POLICY_REVALIDATION_NODES),
        "requires_canonical_release": "canonical_devtool_release" in changed,
        "requires_full_gate_e": bool(changed),
        "fingerprints": fingerprints["nodes"],
    }


def emit_plan_text(obj: dict[str, Any]) -> None:
    print(f"Gate-E reconcile plan: {obj['status']}")
    print(f"  current projection: {obj['current']['source_projection_sha256']}")
    print(f"  prior projection:   {obj['prior']['source_projection_sha256']}")
    if obj.get("changed_execution_nodes"):
        print("  changed expensive/environment nodes:")
        for node in obj["changed_execution_nodes"]:
            print(f"    - {node}")
    elif obj["status"] == "policy-only-requalification-possible":
        print(f"  reusable expensive/environment nodes: {len(obj.get('reusable_execution_nodes') or [])}")
        print(f"  policy nodes to rerun: {len(obj.get('policy_revalidation_nodes') or [])}")
    else:
        print("  source projection unchanged: no Gate-E execution needs to rerun")


def run_checked(command: list[str], *, timeout: int = 300, cwd: Path | None = None) -> None:
    cp = run(*command, timeout=timeout, cwd=cwd)
    sys.stdout.write(cp.stdout)
    sys.stderr.write(cp.stderr)
    if cp.returncode != 0:
        raise RuntimeError(f"policy revalidation failed: {' '.join(command)}")


def run_successor_validators() -> None:
    cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    target = cfg.get("targets", {}).get("dustui_android", {})
    jobs = target.get("jobs", {})
    workflow = target.get("workflows", {}).get("gate_d_successor_validators", [])
    for row in workflow:
        ref = row.get("ref") if isinstance(row, dict) else row
        if not isinstance(ref, str) or not ref.startswith("job:"):
            raise RuntimeError(f"successor validator workflow contains unsupported ref: {ref!r}")
        job_name = ref[4:]
        job = jobs.get(job_name) or {}
        command = job.get("command")
        if not isinstance(command, list) or not command:
            raise RuntimeError(f"successor validator job {job_name} has no command")
        runner = job.get("runner")
        if runner != "command":
            raise RuntimeError(f"successor validator job {job_name} is not a command runner")
        root = target.get("root") or "."
        job_root = job.get("root")
        base = ROOT / root
        if job_root:
            base = (base / job_root).resolve()
        run_checked([str(x) for x in command], timeout=180, cwd=base)


def refresh_retirement() -> None:
    run_checked(["python3", "scripts/audfix16_retirement.py", "finalize"], timeout=180)
    run_checked(["python3", "scripts/audfix16_retirement.py", "check"], timeout=180)


def run_policy_revalidation() -> dict[str, dict[str, Any]]:
    # These checks are intentionally cheap relative to Android lint/build/device
    # work and re-establish current qualification-policy truth.
    run_checked(["python3", "scripts/validate_audfix17_contract.py", "--audit-loop"], timeout=180)
    run_checked(["python3", "scripts/validate_full_roadmap_conformance.py", "--scope", "all"], timeout=180)
    run_successor_validators()

    # Gate-E owned cheap evidence-producing nodes. Go race is environment-bearing
    # and is fingerprint-reused when its Go inputs + race function are unchanged.
    migration = gate.migration_preflight()
    promises = gate.promise_preflight()
    convergence = gate.counterexample_convergence()
    return {
        "final_migration_audit": migration,
        "final_promise_audit": promises,
        "counterexample_convergence": convergence,
    }


def valid_hex64(value: Any) -> bool:
    s = str(value or "")
    return len(s) == 64 and all(c in "0123456789abcdefABCDEF" for c in s)


def build_node(
    node_id: str,
    *,
    ident: dict[str, str],
    qualification_run_id: str,
    candidate: dict[str, Any],
    previous_node: dict[str, Any] | None,
    policy_evidence: dict[str, Any] | None,
) -> dict[str, Any]:
    old_registry = candidate["evidence"]
    old_projection = str(old_registry.get("qualified_source_projection_sha256") or old_registry.get("source_projection_sha256") or "")
    old_head = validated_head(old_registry)
    if node_id in REUSABLE_EXECUTION_NODES:
        if not previous_node:
            raise RuntimeError(f"prior Gate-E evidence is missing reusable node {node_id}")
        fp = node_fingerprint(ident["git_head"], node_id)["fingerprint_sha256"]
        old_binding = copy.deepcopy(previous_node.get("binding") or {})
        binding = {
            **old_binding,
            "source_projection_sha256": ident["source_projection_sha256"],
            "qualification_run_id": qualification_run_id,
            "validated_source_projection_sha256": old_projection,
            "validated_at_git_head": old_binding.get("validated_at_git_head") or old_binding.get("source_git_head") or old_head,
            "input_fingerprint_sha256": fp,
            "evidence_reused": True,
            "reused_from_validation_run_id": old_binding.get("validation_run_id") or old_registry.get("validation_run_id"),
        }
        # Preserve original source_git_* as validation provenance. Do not rewrite
        # them to the successor commit and pretend the node executed there.
        return {"id": node_id, "status": "pass", "binding": binding}

    # Policy nodes executed on the successor source projection.
    binding: dict[str, Any] = {
        "source_projection_sha256": ident["source_projection_sha256"],
        "source_git_head": ident["git_head"],
        "source_git_tree": ident["git_tree"],
        "validated_source_projection_sha256": ident["source_projection_sha256"],
        "validated_at_git_head": ident["git_head"],
        "validation_run_id": qualification_run_id,
        "qualification_run_id": qualification_run_id,
        "input_fingerprint_sha256": ident["source_projection_sha256"],
        "evidence_reused": False,
    }
    if node_id == "go_race_where_supported" and policy_evidence:
        binding["disposition"] = policy_evidence.get("disposition")
    return {"id": node_id, "status": "pass", "binding": binding}


def write_v2_registry(
    *, ident: dict[str, str], candidate: dict[str, Any], policy: dict[str, dict[str, Any]], qualification_run_id: str
) -> tuple[dict[str, Any], dict[str, Any]]:
    old = candidate["evidence"]
    old_nodes = node_map(old)
    release_apk = str(old.get("release_apk_sha256") or "")
    release_report = str(old.get("release_report_sha256") or "")
    signer = str(old.get("signing_identity_sha256") or "")
    runtime = str(old.get("go_runtime_sha256") or "")
    release_run = str(old.get("release_run_id") or "")
    for name, value in {
        "release_apk_sha256": release_apk,
        "release_report_sha256": release_report,
        "signing_identity_sha256": signer,
        "go_runtime_sha256": runtime,
    }.items():
        if not valid_hex64(value):
            raise RuntimeError(f"prior Gate-E evidence has invalid {name}")
    if not release_run:
        raise RuntimeError("prior Gate-E evidence has no release run identity")

    nodes: list[dict[str, Any]] = []
    for node_id in REQUIRED_GATE_E_NODES:
        node = build_node(
            node_id,
            ident=ident,
            qualification_run_id=qualification_run_id,
            candidate=candidate,
            previous_node=old_nodes.get(node_id),
            policy_evidence=policy.get(node_id),
        )
        binding = node["binding"]
        if node_id == "canonical_devtool_release":
            binding.update({
                "release_run_id": release_run,
                "release_report_sha256": release_report,
                "artifact_sha256": release_apk,
                "signing_identity_sha256": signer,
                "runtime_sha256": runtime,
            })
        elif node_id in {"android_release_unit", "android_release_lint", "android_release_assemble"}:
            binding.update({
                "evidence_source": "canonical_devtool_release",
                "producer_run_id": release_run,
                "release_report_sha256": release_report,
                "artifact_sha256": release_apk,
            })
        nodes.append(node)

    old_projection = str(old.get("qualified_source_projection_sha256") or old.get("source_projection_sha256") or "")
    registry = {
        "schema": "dustui.gate-e.evidence-registry.v2",
        "status": "pass",
        "generated_by": "scripts/audfix17_reuse.py reconcile",
        "qualification_identity": "source_projection_sha256",
        "source_projection_sha256": ident["source_projection_sha256"],
        "qualified_source_projection_sha256": ident["source_projection_sha256"],
        "source_git_head": ident["git_head"],
        "source_git_tree": ident["git_tree"],
        "qualified_at_git_head": ident["git_head"],
        "git_identity_role": "provenance-not-validity",
        "gate_d_ancestor_source_sha256": old.get("gate_d_ancestor_source_sha256"),
        "validation_run_id": qualification_run_id,
        "qualification_run_id": qualification_run_id,
        "release_run_id": release_run,
        "release_report_sha256": release_report,
        "release_apk_sha256": release_apk,
        "signing_identity_sha256": signer,
        "go_runtime_sha256": runtime,
        "required_nodes": list(REQUIRED_GATE_E_NODES),
        "nodes": nodes,
        "reuse": {
            "mode": "node-input-fingerprint-composition",
            "prior_evidence_commit": candidate["evidence_commit"],
            "prior_evidence_sha256": candidate["evidence_sha256"],
            "prior_source_projection_sha256": old_projection,
            "reused_execution_nodes": list(REUSABLE_EXECUTION_NODES),
            "revalidated_policy_nodes": list(POLICY_REVALIDATION_NODES),
        },
    }

    old_bundle = candidate.get("bundle") or {}
    final_audit = old_bundle.get("final_migration_audit") or {
        "runtime": "go-native-android",
        "android_ui": "Kotlin/Compose",
        "engine_database": "Go-owned SQLite",
        "python_runtime": "none",
        "cpp_scanner_runtime": "none",
        "room_domain_authority": "none",
        "legacy_destructive_executor": "none",
        "legacy_navigation": "none",
        "open_migration_promises": 0,
    }
    bundle = {
        "schema": "dustui.gate-e.final-bundle.v2",
        "status": "pass",
        "gate": "Gate E",
        "phase": "OV15",
        "aud_fix": "AUD-FIX-17",
        "qualification_identity": "source_projection_sha256",
        "source_projection_sha256": ident["source_projection_sha256"],
        "qualified_source_projection_sha256": ident["source_projection_sha256"],
        "source_git_head": ident["git_head"],
        "source_git_tree": ident["git_tree"],
        "git_identity_role": "provenance-not-validity",
        "gate_d_ancestor_source_sha256": old.get("gate_d_ancestor_source_sha256"),
        "validation_run_id": qualification_run_id,
        "qualification_run_id": qualification_run_id,
        "release_run_id": release_run,
        "release_report_sha256": release_report,
        "release_apk_sha256": release_apk,
        "signing_identity_sha256": signer,
        "go_runtime_sha256": runtime,
        "counterexample_new_root_family_count": 0,
        "go_race_disposition": (policy.get("go_race_where_supported") or {}).get("disposition") or old_bundle.get("go_race_disposition"),
        "final_migration_audit": final_audit,
        "promise_ledger": {
            "open_root_gaps": 0,
            "final_gate_promise": "GAP:AUD-024",
            "status": "closed-by-executed-or-reused-gate-e-evidence",
        },
        "evidence_registry": EVIDENCE_REL,
        "reuse": registry["reuse"],
    }
    gate.write_json(EVIDENCE, registry)
    gate.write_json(BUNDLE, bundle)
    return registry, bundle


def restore_or_migrate_same_projection(ident: dict[str, str], candidate: dict[str, Any]) -> dict[str, Any]:
    old = candidate["evidence"]
    # If v2 evidence is already live, no tracked file needs to change merely
    # because HEAD changed. Commit/tree are provenance, not validity.
    if candidate["source"] == "live" and old.get("schema") == "dustui.gate-e.evidence-registry.v2":
        run_checked(["python3", "scripts/refresh_qualification_state.py"], timeout=180)
        run_checked(["python3", "scripts/refresh_acceptance_state.py"], timeout=180)
        state = load_json(STATE)
        if not state.get("gate_e", {}).get("qualified"):
            raise RuntimeError("projection-matched v2 evidence did not preserve Gate-E qualification")
        return {
            "status": "qualified-projection-unchanged",
            "source_projection_sha256": ident["source_projection_sha256"],
            "evidence_rewritten": False,
        }

    # A v1 registry (or a registry recovered from Git history) can be migrated
    # without rerunning execution because the entire qualification projection is
    # identical. Preserve original node execution provenance.
    qualification_run_id = os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID") or f"reconcile-{ident['git_head'][:12]}"
    old_nodes = node_map(old)
    nodes: list[dict[str, Any]] = []
    for node_id in REQUIRED_GATE_E_NODES:
        prev = copy.deepcopy(old_nodes.get(node_id) or {})
        if prev.get("status") != "pass":
            raise RuntimeError(f"prior same-projection evidence node is not passing: {node_id}")
        binding = prev.setdefault("binding", {})
        binding.setdefault("validated_source_projection_sha256", ident["source_projection_sha256"])
        binding.setdefault("validated_at_git_head", binding.get("source_git_head") or validated_head(old))
        binding.setdefault("qualification_run_id", qualification_run_id)
        binding["evidence_reused"] = True
        if node_id in REUSABLE_EXECUTION_NODES:
            binding["input_fingerprint_sha256"] = node_fingerprint(ident["git_head"], node_id)["fingerprint_sha256"]
        else:
            binding["input_fingerprint_sha256"] = ident["source_projection_sha256"]
        nodes.append({"id": node_id, "status": "pass", "binding": binding})
    registry = dict(old)
    registry.update({
        "schema": "dustui.gate-e.evidence-registry.v2",
        "generated_by": "scripts/audfix17_reuse.py reconcile",
        "qualification_identity": "source_projection_sha256",
        "qualified_source_projection_sha256": ident["source_projection_sha256"],
        "git_identity_role": "provenance-not-validity",
        "nodes": nodes,
        "reuse": {
            "mode": "same-source-projection",
            "prior_evidence_commit": candidate["evidence_commit"],
            "prior_evidence_sha256": candidate["evidence_sha256"],
            "prior_source_projection_sha256": ident["source_projection_sha256"],
            "reused_execution_nodes": list(REQUIRED_GATE_E_NODES),
            "revalidated_policy_nodes": [],
        },
    })
    bundle = dict(candidate.get("bundle") or {})
    bundle.update({
        "schema": "dustui.gate-e.final-bundle.v2",
        "qualification_identity": "source_projection_sha256",
        "qualified_source_projection_sha256": ident["source_projection_sha256"],
        "git_identity_role": "provenance-not-validity",
        "reuse": registry["reuse"],
    })
    gate.write_json(EVIDENCE, registry)
    gate.write_json(BUNDLE, bundle)
    run_checked(["python3", "scripts/refresh_qualification_state.py"], timeout=180)
    run_checked(["python3", "scripts/refresh_acceptance_state.py"], timeout=180)
    return {
        "status": "qualified-projection-unchanged",
        "source_projection_sha256": ident["source_projection_sha256"],
        "evidence_rewritten": True,
    }


def clear_stale_live_derived_evidence() -> None:
    """Remove stale live Gate-E outputs before rebuilding successor policy truth.

    Gate-E evidence and bundle are excluded from the source projection, so a
    successor source commit may legitimately leave the prior generated files
    present.  Once the projection changes those live files are no longer the
    current qualification authority.  Keeping them in place while refreshing
    canonical state would transiently classify Gate E as
    ``qualification-evidence-invalid`` and block the policy validators that are
    supposed to establish the successor seal.  Candidate evidence has already
    been loaded into memory (or recovered from Git history), so removing these
    derived files is safe and avoids a self-poisoning reconcile loop.
    """
    for path in (EVIDENCE, BUNDLE):
        try:
            path.unlink()
        except FileNotFoundError:
            pass


def reconcile() -> dict[str, Any]:
    ident = current_identity()
    candidate = find_candidate()
    old = candidate["evidence"]
    old_projection = str(old.get("qualified_source_projection_sha256") or old.get("source_projection_sha256") or "")
    if old_projection == ident["source_projection_sha256"]:
        return restore_or_migrate_same_projection(ident, candidate)

    p = plan()
    changed = list(p.get("changed_execution_nodes") or [])
    if changed:
        detail = ", ".join(changed)
        release = " Run ./devtoolw gate-e-release first." if p.get("requires_canonical_release") else ""
        raise RuntimeError(
            "Gate-E execution reuse refused because expensive/environment-bearing inputs changed: "
            + detail + "." + release + " Then run the canonical Gate-E workflow (or Devtool node-resume when available)."
        )

    # Stale generated Gate-E files are not source identity. Clear them before
    # state refresh so successor policy validation sees the canonical eligible
    # phase instead of a transient evidence-invalid phase. The prior evidence
    # is already retained in ``candidate`` for lineage and execution reuse.
    clear_stale_live_derived_evidence()

    # Policy-only successor: refresh the excluded AUD-FIX-16 projection record,
    # rerun cheap policy truth, then compose unchanged execution evidence.
    refresh_retirement()
    policy = run_policy_revalidation()
    qualification_run_id = os.environ.get("DEVTOOL_RUN_ID") or os.environ.get("DEVTOOL_RUN_UUID")
    if not qualification_run_id:
        raise RuntimeError("Gate-E policy requalification must run through Devtool to provide a qualification run identity")
    registry, bundle = write_v2_registry(
        ident=ident,
        candidate=candidate,
        policy=policy,
        qualification_run_id=qualification_run_id,
    )
    run_checked(["python3", "scripts/refresh_qualification_state.py"], timeout=180)
    run_checked(["python3", "scripts/refresh_acceptance_state.py"], timeout=180)
    run_checked(["python3", "scripts/validate_full_roadmap_conformance.py", "--scope", "AUD-FIX-17"], timeout=180)
    state = load_json(STATE)
    if state.get("gate_e", {}).get("qualified") is not True:
        raise RuntimeError("Gate-E reuse evidence was composed but canonical state did not qualify Gate E")
    return {
        "status": "qualified-by-policy-revalidation-and-node-reuse",
        "source_projection_sha256": ident["source_projection_sha256"],
        "qualification_run_id": qualification_run_id,
        "reused_execution_nodes": list(REUSABLE_EXECUTION_NODES),
        "revalidated_policy_nodes": list(POLICY_REVALIDATION_NODES),
        "release_run_id": registry["release_run_id"],
        "evidence_schema": registry["schema"],
        "bundle_schema": bundle["schema"],
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("action", choices=["plan", "reconcile"])
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    try:
        obj = plan() if args.action == "plan" else reconcile()
    except Exception as exc:
        print(f"AUD-FIX-17 reuse {args.action} failure: {exc}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(obj, indent=2, sort_keys=True))
    elif args.action == "plan":
        emit_plan_text(obj)
    else:
        print(f"AUD-FIX-17 reuse reconcile PASS: {obj['status']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
