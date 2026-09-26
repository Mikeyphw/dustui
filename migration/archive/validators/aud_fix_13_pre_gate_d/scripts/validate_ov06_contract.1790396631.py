#!/usr/bin/env python3
"""Validate DustUI OV06 privileged Go runtime boundary contracts."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def run_json(args: list[str]) -> dict:
    proc = subprocess.run(args, cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    return json.loads(proc.stdout)


def validate(audit_loop: bool = False) -> list[str]:
    failures: list[str] = []

    devtool = tomllib.loads(read(".devtool.toml"))
    targets = devtool.get("targets", {})
    android = targets.get("dustui_android", {})
    go_target = targets.get("dustui_go", {})
    android_meta = android.get("metadata", {})
    go_meta = go_target.get("metadata", {})

    require(android_meta.get("migration_phase") == "OV06", "dustui_android migration_phase must be OV06", failures)
    require(go_meta.get("migration_phase") == "OV06", "dustui_go migration_phase must be OV06", failures)
    require(android_meta.get("android_buildability") == "intentionally-suspended", "Android buildability must remain intentionally suspended", failures)
    require(android_meta.get("android_buildability_suspended_until") == "OV13", "Android reconnect gate must remain OV13", failures)
    require(android_meta.get("gate") == "post-Gate-B non-gate", "OV06 must declare post-Gate-B non-gate status", failures)
    require(go_meta.get("runtime_contract_gate") == "Gate B", "runtime Gate B contract must remain recorded", failures)
    require(go_meta.get("privileged_runtime_boundary") is True, "dustui_go must declare privileged runtime boundary", failures)
    require(go_meta.get("root_protocol") == "dustui.privileged.runtime.v1", "dustui_go root protocol metadata missing", failures)
    require(go_meta.get("read_authority") == "go-sqlite", "read authority must remain go-sqlite", failures)
    require("jobs" not in android and "workflows" not in android, "unsupported Devtool jobs/workflows must not be reintroduced", failures)

    for rel in [
        "go/internal/privileged/boundary.go",
        "go/internal/privileged/boundary_test.go",
        "go/internal/state/schema.go",
        "go/internal/readiness/readiness.go",
        "go/cmd/dustui-go/main.go",
    ]:
        require((ROOT / rel).is_file(), f"OV06 file missing: {rel}", failures)

    privileged = run_json(["go", "run", "./cmd/dustui-go", "privileged-contract"])
    require(privileged.get("protocol") == "dustui.privileged.runtime.v1", "privileged protocol must be dustui.privileged.runtime.v1", failures)
    require(privileged.get("kernelProtocol") == "dustui.kernel.v1", "kernel protocol must remain dustui.kernel.v1", failures)
    require(privileged.get("rootNamespace") == "dustui.root.v1", "root namespace contract missing", failures)
    require(privileged.get("runtimeIdentity") == "dustui-go-root-runtime", "runtime identity contract missing", failures)
    require(privileged.get("genericShell") is False, "generic shell must be absent", failures)
    require(privileged.get("productionMutation") is False, "OV06 must not claim production mutation", failures)
    require(privileged.get("maxQueueDepth", 0) > 0, "queue must be bounded", failures)
    require(privileged.get("maxWorkers", 0) > 0, "workers must be bounded", failures)
    require(privileged.get("maxPayloadBytes", 0) <= 65536, "privileged payload must be tightly bounded", failures)
    require(privileged.get("maxResponseBytes", 0) <= 65536, "privileged response must be tightly bounded", failures)
    require(privileged.get("maxEventBytes", 0) <= 32768, "privileged event must be tightly bounded", failures)
    operations = {op.get("name"): op for op in privileged.get("operations", [])}
    for op in ["root.readiness", "root.status", "root.reconcile", "cleanup.preview", "cleanup.execute.dry_run"]:
        require(op in operations, f"privileged operation missing: {op}", failures)
    for forbidden in ["shell", "root.shell", "exec", "cleanup.execute"]:
        require(forbidden not in operations, f"forbidden privileged operation admitted: {forbidden}", failures)
    dry_run = operations.get("cleanup.execute.dry_run", {})
    require(dry_run.get("class") == "destructive", "dry-run execution must exercise destructive-class admission", failures)
    require("dry_run_mutation" in dry_run.get("capabilityClasses", []), "dry-run capability class missing", failures)

    readiness = run_json(["go", "run", "./cmd/dustui-go", "readiness"])
    require(readiness.get("migrationPhase") == "OV06", "readiness phase must be OV06", failures)
    require(readiness.get("privilegedBoundary") is True, "readiness must expose privileged boundary", failures)
    require(readiness.get("mutationAuthority") is False, "OV06 must not claim mutation authority", failures)

    runtime = run_json(["go", "run", "./cmd/dustui-go", "runtime-contract"])
    require(runtime.get("privilegedBoundary") == "go-root-admission-boundary-ov06", "runtime contract must reference OV06 privileged boundary", failures)
    require(runtime.get("cleanupAuthority") == "legacy-constrained-until-OV08", "cleanup authority must not cut over before OV08", failures)

    schema = run_json(["go", "run", "./cmd/dustui-go", "schema"])
    require(schema.get("schemaVersion") == 3, "OV06 schema version must be 3", failures)
    tables = schema.get("requiredTables", [])
    for table in ["root_runtime_identities", "privileged_admissions", "privileged_events"]:
        require(table in tables, f"schema must include {table}", failures)
    joined_schema = "\n".join(schema.get("createStatements", []))
    for token in ["no_replay_token", "reconcile_required", "target_scope", "runtime_identity", "root_namespace"]:
        require(token in joined_schema, f"schema missing privileged token {token}", failures)

    for rel in [
        "migration/contracts/v1/privileged_runtime_protocol_ov06.md",
        "migration/contracts/v1/root_boundary_ov06.md",
        "migration/contracts/v1/authority_transition_ov06.md",
        "migration/contracts/v1/legacy_paths_ov06.md",
        "migration/contracts/v1/source_token_retirement_ov06.md",
        "migration/audit/v1/ov06_audit_loop.md",
        "migration/ledger/ov06_status.json",
    ]:
        require((ROOT / rel).is_file(), f"OV06 contract missing: {rel}", failures)

    findings = json.loads(read("migration/ledger/findings.json"))
    require(findings.get("phase") == "OV06", "migration ledger phase must advance to OV06", failures)
    by_id = {item.get("id"): item for item in findings.get("findings", [])}
    for fid in ["RC-05", "N-04", "ABH-02"]:
        require(fid in by_id and "OV06" in by_id[fid].get("overlays", []), f"OV06 ledger mapping missing: {fid}", failures)
        require(by_id[fid].get("ov06_status"), f"OV06 ledger status missing: {fid}", failures)

    if audit_loop:
        combined = "\n".join(read(rel) for rel in [
            "migration/contracts/v1/privileged_runtime_protocol_ov06.md",
            "migration/contracts/v1/root_boundary_ov06.md",
            "migration/contracts/v1/authority_transition_ov06.md",
            "migration/contracts/v1/legacy_paths_ov06.md",
            "migration/contracts/v1/source_token_retirement_ov06.md",
            "migration/audit/v1/ov06_audit_loop.md",
        ])
        for phrase in [
            "allowlist",
            "bounded",
            "single-flight",
            "reconcile",
            "forbids blind replay",
            "generic shell",
            "OV08",
            "intentionally suspended",
        ]:
            require(phrase in combined, f"OV06 audit loop missing: {phrase}", failures)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV06 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV06 privileged runtime boundary contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
