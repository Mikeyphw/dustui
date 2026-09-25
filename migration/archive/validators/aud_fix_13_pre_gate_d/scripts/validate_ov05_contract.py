#!/usr/bin/env python3
"""Validate DustUI OV05 Go runtime/read-authority Gate B contracts."""
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

    require(android_meta.get("migration_phase") == "OV05", "dustui_android migration_phase must be OV05", failures)
    require(go_meta.get("migration_phase") == "OV05", "dustui_go migration_phase must be OV05", failures)
    require(android_meta.get("android_buildability") == "intentionally-suspended", "Android buildability must remain intentionally suspended", failures)
    require(android_meta.get("android_buildability_suspended_until") == "OV13", "Android reconnect gate must remain OV13", failures)
    require(android_meta.get("gate") == "Gate B", "OV05 must declare Gate B", failures)
    require(go_meta.get("runtime_contract_gate") == "Gate B", "dustui_go must declare runtime Gate B", failures)
    require(go_meta.get("read_authority") == "go-sqlite", "dustui_go must declare go-sqlite read authority", failures)
    require(go_meta.get("android_runtime_packaging") == "arm64-v8a-only", "dustui_go must declare arm64 runtime package contract", failures)
    require(go_meta.get("shell_scaffold") is True, "dustui_go must declare shell scaffold", failures)
    require("jobs" not in android and "workflows" not in android, "unsupported Devtool jobs/workflows must not be reintroduced", failures)

    for rel in [
        "go/internal/runtime/spec.go",
        "go/internal/runtime/spec_test.go",
        "go/internal/state/read_authority.go",
        "go/internal/state/read_authority_test.go",
        "go/internal/shell/routes.go",
        "go/internal/shell/routes_test.go",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeSessionContract.kt",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustShellRouteContract.kt",
    ]:
        require((ROOT / rel).is_file(), f"OV05 file missing: {rel}", failures)

    app_build = read("android/app/build.gradle.kts")
    require('abiFilters += listOf("arm64-v8a")' in app_build, "production ABI must be arm64-v8a only", failures)
    require('abiFilters += listOf("arm64-v8a", "x86_64")' not in app_build, "x86_64 must not remain in production abiFilters", failures)

    runtime_contract = run_json(["go", "run", "./cmd/dustui-go", "runtime-contract"])
    require(runtime_contract.get("productionAbi") == "arm64-v8a", "runtime contract production ABI must be arm64-v8a", failures)
    require(runtime_contract.get("runtimeLibraryName") == "libdustui_go.so", "runtime library name contract missing", failures)
    require(runtime_contract.get("scanReadAuthority") == "go-sqlite", "runtime contract must declare go-sqlite read authority", failures)
    require(runtime_contract.get("requiresRequestId") is True, "runtime contract must require request IDs", failures)
    require(runtime_contract.get("requiresSequence") is True, "runtime contract must require sequences", failures)
    require(runtime_contract.get("requiresReconnect") is True, "runtime contract must require reconnect", failures)
    require(runtime_contract.get("maxFrameBytes") == 1048576, "runtime contract must bound frames at 1MiB", failures)

    shell_contract = run_json(["go", "run", "./cmd/dustui-go", "shell-contract"])
    for route in ["overview", "files", "clean", "apps", "activity", "system"]:
        require(route in shell_contract.get("routes", []), f"shell route missing: {route}", failures)
    for state in ["loading", "empty", "blocked", "error", "content"]:
        require(state in shell_contract.get("states", []), f"shell state missing: {state}", failures)
    require(shell_contract.get("provisionalGrouping") is True, "shell route grouping must remain provisional", failures)
    require(shell_contract.get("androidReconnectGate") == "OV13", "shell reconnect gate must be OV13", failures)

    readiness = run_json(["go", "run", "./cmd/dustui-go", "readiness"])
    require(readiness.get("migrationPhase") == "OV05", "readiness phase must be OV05", failures)
    require(readiness.get("readAuthority") is True, "readiness must mark read authority", failures)
    require(readiness.get("runtimePackaged") is True, "readiness must mark runtime packaged", failures)
    require(readiness.get("shellScaffold") is True, "readiness must mark shell scaffold", failures)
    require(readiness.get("mutationAuthority") is False, "OV05 must not claim mutation authority", failures)

    schema = run_json(["go", "run", "./cmd/dustui-go", "schema"])
    require(schema.get("schemaVersion") == 2, "OV05 schema version must be 2", failures)
    tables = schema.get("requiredTables", [])
    require("runtime_sessions" in tables, "schema must include runtime_sessions", failures)
    require("shell_routes" in tables, "schema must include shell_routes", failures)
    authority = schema.get("ov05Authority", [])
    require(any(x.get("source") == "go_sqlite" and x.get("mayWriteNew") is True for x in authority), "Go SQLite write authority missing", failures)
    require(all(x.get("mayWriteNew") is False for x in authority if x.get("source") == "room_v3"), "Room v3 must be import/read-only", failures)

    for rel in [
        "migration/contracts/v1/authority_transition_ov05.md",
        "migration/contracts/v1/android_runtime_packaging_ov05.md",
        "migration/contracts/v1/shell_scaffold_ov05.md",
        "migration/contracts/v1/legacy_paths_ov05.md",
        "migration/contracts/v1/source_token_retirement_ov05.md",
        "migration/audit/v1/ov05_gateB_audit_loop.md",
        "migration/ledger/ov05_gateB_status.json",
    ]:
        require((ROOT / rel).is_file(), f"OV05 contract missing: {rel}", failures)

    findings = json.loads(read("migration/ledger/findings.json"))
    require(findings.get("phase") == "OV05", "migration ledger phase must advance to OV05", failures)
    by_id = {item.get("id"): item for item in findings.get("findings", [])}
    for fid in ["RC-01", "RC-06", "RC-07", "ABH-01", "ABH-03", "ABH-04", "ABH-06", "ABH-07", "ABH-16"]:
        require(fid in by_id and "OV05" in by_id[fid].get("overlays", []), f"OV05 ledger mapping missing: {fid}", failures)
        require(by_id[fid].get("ov05_status"), f"OV05 ledger status missing: {fid}", failures)

    if audit_loop:
        combined = "\n".join(read(rel) for rel in [
            "migration/contracts/v1/authority_transition_ov05.md",
            "migration/contracts/v1/android_runtime_packaging_ov05.md",
            "migration/contracts/v1/shell_scaffold_ov05.md",
            "migration/contracts/v1/legacy_paths_ov05.md",
            "migration/contracts/v1/source_token_retirement_ov05.md",
            "migration/audit/v1/ov05_gateB_audit_loop.md",
        ])
        for phrase in [
            "intentionally-suspended",
            "No Room/Go dual writer",
            "fixed executable path",
            "bounded",
            "request IDs",
            "sequence",
            "stale",
            "provisional",
            "No hidden Python/C++ fallback",
            "unsupported TOML schema",
        ]:
            require(phrase in combined, f"OV05 audit loop missing: {phrase}", failures)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV05 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV05 Go runtime/read-authority Gate B contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
