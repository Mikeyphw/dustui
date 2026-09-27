#!/usr/bin/env python3
"""Static/topology contract for AUD-FIX-17 Gate E.

This validator never manufactures final evidence. It proves only that the final
qualification graph is present, fail-closed, and still anchored to the already
qualified Gate-D/AUD-FIX-16 state.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from pathlib import Path

from audfix01_core import REQUIRED_GATE_E_NODES, load_json, repository_observations

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def audit() -> list[str]:
    failures: list[str] = []
    req = lambda cond, msg: failures.append(msg) if not cond else None

    state = load_json(ROOT / "migration/qualification/v1/current_state.json")
    req(state.get("gate_d", {}).get("qualified") is True, "Gate D is not qualified")
    req(state.get("ov14", {}).get("status") == "physically-retired", "AUD-FIX-16 physical retirement is not complete")
    req(state.get("gate_e", {}).get("status") in {"eligible-for-qualification", "qualified"}, "Gate E is neither eligible nor qualified")

    topology_path = ROOT / "migration/qualification/v1/gate_e_topology.json"
    req(topology_path.is_file(), "Gate-E topology missing")
    if topology_path.is_file():
        topology = load_json(topology_path)
        req(topology.get("schema") == "dustui.gate-e.topology.v2", "Gate-E topology schema mismatch")
        req(topology.get("required_nodes") == list(REQUIRED_GATE_E_NODES), "Gate-E required node order/topology mismatch")
        policy = topology.get("environment_policy") or {}
        req(policy.get("rooted_arm64") == "required", "rooted ARM64 must be required")
        req(policy.get("multi_user_or_work_profile") == "conditional", "multi-user/work-profile evidence must be conditional when unavailable on the qualification device")
        req(policy.get("device_instrumentation") == "required", "device instrumentation must be required")
        req(policy.get("signed_release") == "required", "signed release must be required")
        reuse_policy = topology.get("reuse_policy") or {}
        req(reuse_policy.get("qualification_identity") == "source_projection_sha256", "Gate-E qualification identity must be the source projection")
        req(reuse_policy.get("git_identity_role") == "provenance-not-validity", "Git identity must be provenance, not qualification validity")
        req(reuse_policy.get("generic_execution_cache_owner") == "Devtool", "generic execution caching must remain Devtool-owned")

    cfg = tomllib.loads(read(".devtool.toml"))
    android = cfg.get("targets", {}).get("dustui_android", {})
    workflows = android.get("workflows", {})
    jobs = android.get("jobs", {})
    wrapper = cfg.get("wrapper", {}).get("commands", {})
    release = (android.get("android") or {}).get("release") or {}

    req("gate_e" in workflows, "Gate-E workflow missing")
    if "gate_e" in workflows:
        ids = [x.get("id") for x in workflows["gate_e"] if isinstance(x, dict)]
        for node in [
            "contract", "canonical_release", "go_core", "go_adversarial", "android_debug_unit",
            "android_release_unit", "instrumentation_full", "rooted_arm64", "runtime_binding",
            "migration_audit", "promise_audit", "counterexample_convergence", "finalize", "postseal_audit",
        ]:
            req(node in ids, f"Gate-E workflow node missing: {node}")

    for job in [
        "audfix17_contract", "audfix17_release_evidence", "audfix17_go_race",
        "audfix17_instrumentation_full", "audfix17_rooted_arm64_runtime",
        "audfix17_runtime_apk_binding", "audfix17_migration_preflight",
        "audfix17_promise_preflight", "audfix17_counterexample_convergence",
        "audfix17_finalize", "audfix17_postseal_audit",
        "audfix17_reuse_plan", "audfix17_reconcile",
    ]:
        req(job in jobs, f"Gate-E target-local job missing: {job}")

    req(wrapper.get("aud-fix-17", {}).get("workflow") == "gate_e", "aud-fix-17 wrapper is missing/misbound")
    req(wrapper.get("gate-e-plan", {}).get("workflow") == "gate_e_reuse_plan", "gate-e-plan wrapper is missing/misbound")
    req(wrapper.get("gate-e-reconcile", {}).get("workflow") == "gate_e_reconcile", "gate-e-reconcile wrapper is missing/misbound")
    req("gate_e_reuse_plan" in workflows, "Gate-E reuse-plan workflow missing")
    req("gate_e_reconcile" in workflows, "Gate-E reconcile workflow missing")
    req("gate_e_fast" not in workflows, "qualified source still exposes temporary gate_e_fast resume workflow")
    req("aud-fix-17-fast" not in wrapper, "qualified source still exposes temporary aud-fix-17-fast wrapper")
    req("gate-e-release" not in wrapper, "gate-e-release must not use wrapper.commands; nested Android release is launcher-owned")
    launcher = read("devtoolw")
    req(
        "gate-e-release|release-final|ov15-release" in launcher
        and '--target dustui_android release "$@"' in launcher,
        "gate-e-release launcher must dispatch directly to top-level canonical Devtool release",
    )

    req(release.get("variant") == "release", "Devtool Android release variant must be release")
    req(release.get("unit_tests") is True, "canonical release must run unit tests")
    req(release.get("lint") is True, "canonical release must run lint")
    req(release.get("device_tests") == "required", "canonical release must require device tests")
    req(release.get("install_smoke") is True, "canonical release must require install smoke")
    req(release.get("verify_signature") is True, "canonical release must verify APK signature")
    req(release.get("verify_certificate_identity") is True, "canonical release must verify certificate identity")

    bridge = read("scripts/audfix15_android_job_bridge.py")
    req('"--gradle-arg"' in bridge and "extra_gradle_args" in bridge, "Android Devtool bridge cannot pass Gate-E instrumentation runner arguments")
    gate_script = read("scripts/audfix17_gate_e.py")
    identity_script = read("scripts/audfix17_identity.py")
    reuse_script = read("scripts/audfix17_reuse.py")
    req(".devtool/reports/android-release" in gate_script, "Gate-E script does not consume canonical Devtool release evidence")
    req("release_report_sha256" in gate_script and "signing_identity_sha256" in gate_script, "release report/signing cross-binding missing")
    req("source_projection_sha256" in gate_script and "validation_run_id" in gate_script and "release_run_id" in gate_script, "validation/release source cross-binding incomplete")
    req("dustui.gate-e.evidence-registry.v2" in gate_script, "fresh Gate-E finalization does not emit v2 evidence")
    req("REUSABLE_EXECUTION_NODES" in identity_script and "POLICY_REVALIDATION_NODES" in identity_script, "Gate-E node fingerprint partition missing")
    req("provenance-not-validity" in reuse_script and "policy-only-requalification-possible" in reuse_script, "Gate-E reconcile semantics missing")
    req("Gate-E reconcile requires a clean committed worktree" in reuse_script, "Gate-E reconcile is not commit-bound")

    inst_job = json.dumps(jobs.get("audfix17_instrumentation_full") or {})
    req("dustui.requireMultiUser=true" in inst_job, "Gate-E instrumentation does not require multi-user/work-profile evidence")
    req(":app:connectedDebugAndroidTest" in inst_job, "Gate-E full connected instrumentation task missing")

    obs = repository_observations(ROOT)
    req(not obs.get("legacy_python_textual_present"), "Python/Textual production predecessor returned")
    req(not obs.get("python_textual_oracle_present"), "live Python/Textual oracle returned")
    req(not obs.get("room_artifacts_present"), "Room authority returned")
    req(not obs.get("ndjson_lifecycle_present"), "NDJSON file-tail lifecycle returned")
    req(not obs.get("generic_tooling_duplication_paths"), "repo-local generic Android tooling returned")
    req(not obs.get("predecessor_source_token_paths"), "predecessor source-token validators returned")
    req(obs.get("android_go_process_binding_present") and obs.get("android_go_packaging_present"), "Android Go runtime binding/packaging missing")
    req(obs.get("go_sqlite_implementation_present"), "Go-owned SQLite implementation missing")

    live_docs = {
        rel: read(rel)
        for rel in (
            "README.md",
            "RELEASE_CHECKLIST.md",
            "VALIDATION.md",
            "KNOWN_ISSUES.md",
            "android/VALIDATION_REMEDIATION.md",
            "migration/qualification/v1/README.md",
        )
    }
    stale_live_claims = (
        "Gate E remains pending",
        "Gate E is the next qualification boundary",
        "Gate D remains open",
        "blocks `scripts/final-rc-gate.sh`",
        "`release-gate.sh` runs the same audit",
    )
    for rel, body in live_docs.items():
        for stale in stale_live_claims:
            req(stale not in body, f"live operator doc {rel} retains stale post-Gate-E claim: {stale}")
    req("Gate E is qualified" in live_docs["README.md"], "README does not state current qualified Gate-E truth")
    req("Gate E is qualified" in live_docs["migration/qualification/v1/README.md"], "qualification README does not state current qualified Gate-E truth")

    req(not (ROOT / "pyproject.toml").exists(), "root pyproject.toml must remain absent")
    main_vm = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    req("AtomicLong" not in main_vm, "MainViewModel reintroduced forbidden AtomicLong")

    gate_e = state.get("gate_e", {})
    evidence = ROOT / "migration/qualification/v1/gate_e_evidence.json"
    bundle = ROOT / "migration/qualification/v1/gate_e_bundle.json"
    if gate_e.get("qualified"):
        req(evidence.is_file(), "qualified Gate E missing evidence registry")
        req(bundle.is_file(), "qualified Gate E missing final bundle")
        if bundle.is_file():
            b = load_json(bundle)
            req(b.get("status") == "pass", "Gate-E final bundle is not passing")
            req(b.get("schema") in {"dustui.gate-e.final-bundle.v1", "dustui.gate-e.final-bundle.v2"}, "Gate-E final bundle schema is unsupported")
            audit_obj = b.get("final_migration_audit") or {}
            req(audit_obj.get("open_migration_promises") == 0, "qualified Gate E still reports open migration promises")
            req(b.get("counterexample_new_root_family_count") == 0, "qualified Gate E has unresolved counterexample family")

    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    args = ap.parse_args()
    failures = audit()
    if args.audit_loop and not failures:
        failures = ["second-pass:" + x for x in audit()]
    if failures:
        for item in failures:
            print("AUD-FIX-17 contract failure:", item, file=sys.stderr)
        return 1
    print("AUD-FIX-17 Gate-E contract PASS; final seal is fail-closed on canonical release + executed device/adversarial evidence")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
