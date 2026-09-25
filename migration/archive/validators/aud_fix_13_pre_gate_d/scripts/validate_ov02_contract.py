#!/usr/bin/env python3
"""Validate DustUI OV02 Go state/protocol/operation kernel contracts."""
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


def run_go_schema() -> dict:
    proc = subprocess.run(["go", "run", "./cmd/dustui-go", "schema"], cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    return json.loads(proc.stdout)


def validate(audit_loop: bool = False) -> list[str]:
    failures: list[str] = []
    devtool = tomllib.loads(read(".devtool.toml"))
    android = devtool.get("targets", {}).get("dustui_android", {})
    go_target = devtool.get("targets", {}).get("dustui_go", {})
    require(android.get("metadata", {}).get("migration_phase") == "OV02", "dustui_android migration_phase must be OV02", failures)
    require(go_target.get("metadata", {}).get("migration_phase") == "OV02", "dustui_go migration_phase must be OV02", failures)
    require("jobs" not in android and "workflows" not in android, "unsupported Devtool jobs/workflows must not be reintroduced before gate", failures)

    required_dirs = ["engine", "protocol", "state", "operations", "evidence", "scan", "query", "cleanup", "diagnostics", "platform"]
    for name in required_dirs:
        require((ROOT / "go" / "internal" / name).is_dir(), f"Go package boundary missing: {name}", failures)

    for rel in [
        "go/internal/protocol/reason.go",
        "go/internal/protocol/envelope.go",
        "go/internal/state/schema.go",
        "go/internal/state/room_import.go",
        "go/internal/state/migration.go",
        "go/internal/operations/state.go",
    ]:
        require((ROOT / rel).is_file(), f"required Go kernel file missing: {rel}", failures)

    protocol_text = read("go/internal/protocol/reason.go")
    for token in [
        "duplicate_request_mismatch",
        "stale_generation",
        "late_event",
        "terminal_state_sticky",
        "cancellation_sticky",
        "corrupt_state_recovery_required",
    ]:
        require(token in protocol_text, f"stable reason code missing: {token}", failures)

    operations_text = read("go/internal/operations/state.go")
    for state in ["queued", "running", "cancelling", "success", "failure", "cancelled", "interrupted", "unknown", "recovery_required"]:
        require(state in operations_text, f"operation state missing: {state}", failures)
    for token in ["Fingerprint", "LastSequence", "TerminalSequence", "RecoveryRequired", "ReasonLateEvent", "ReasonTerminalStateSticky"]:
        require(token in operations_text, f"operation FSM token missing: {token}", failures)

    schema = run_go_schema()
    required_tables = {
        "scan_sessions", "scan_entries", "scan_contexts", "operations", "operation_events",
        "cleanup_manifests", "cleanup_capabilities", "cleanup_results", "package_inventory_snapshots",
        "analysis_results", "evidence_generations", "problems", "run_history",
    }
    require(required_tables.issubset(set(schema.get("requiredTables", []))), "Go SQLite required table list incomplete", failures)
    joined = "\n".join(schema.get("createStatements", []))
    for token in ["journal_mode = WAL", "expected_generation", "request_fingerprint", "retained_legacy_quarantine_json"]:
        require(token in joined, f"SQLite schema token missing: {token}", failures)
    source_tables = {item.get("sourceTable") for item in schema.get("roomV3ImportPlan", [])}
    for table in ["scan_sessions", "scan_entries", "cleanup_operations", "cleanup_plan_items", "quarantine_entries"]:
        require(table in source_tables, f"Room v3 import source missing: {table}", failures)
    require(all(not item.get("newWrites") for item in schema.get("roomV3ImportPlan", [])), "Room import plan must not permit new writes", failures)

    for rel in [
        "migration/contracts/v1/authority_transition_ov02.md",
        "migration/contracts/v1/room_v3_import_ov02.md",
        "migration/contracts/v1/legacy_paths_ov02.md",
        "migration/contracts/v1/source_token_retirement_ov02.md",
        "migration/audit/v1/ov02_audit_loop.md",
        "migration/ledger/ov02_status.json",
    ]:
        require((ROOT / rel).is_file(), f"OV02 contract missing: {rel}", failures)

    findings = json.loads(read("migration/ledger/findings.json"))
    require(findings.get("phase") == "OV02", "migration ledger phase must advance to OV02", failures)
    ov02_items = [item for item in findings.get("findings", []) if "OV02" in item.get("overlays", [])]
    require(ov02_items, "migration ledger must contain OV02 mapped findings", failures)
    require(all(item.get("ov02_status") == "foundation_established_non_production" for item in ov02_items), "OV02 mapped findings need ov02_status", failures)

    if audit_loop:
        combined = "\n".join(read(rel) for rel in [
            "migration/contracts/v1/authority_transition_ov02.md",
            "migration/contracts/v1/room_v3_import_ov02.md",
            "migration/contracts/v1/legacy_paths_ov02.md",
            "migration/contracts/v1/source_token_retirement_ov02.md",
            "migration/audit/v1/ov02_audit_loop.md",
        ])
        for phrase in [
            "no Room/Go dual writer",
            "quarantine_entries",
            "Failed migrations roll back",
            "No Android production runtime",
            "No unsupported Devtool target-local jobs/workflows",
        ]:
            require(phrase in combined, f"OV02 audit loop missing: {phrase}", failures)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV02 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV02 Go state/protocol kernel contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
