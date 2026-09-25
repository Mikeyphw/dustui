#!/usr/bin/env python3
"""Validate DustUI OV04 query/search/history contracts."""
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

    require(android_meta.get("migration_phase") == "OV04", "dustui_android migration_phase must be OV04", failures)
    require(go_meta.get("migration_phase") == "OV04", "dustui_go migration_phase must be OV04", failures)
    require(android_meta.get("android_buildability") == "intentionally-suspended", "Android buildability suspension must remain explicit", failures)
    require(android_meta.get("android_buildability_suspended_until") == "OV13", "Android suspension end gate must remain OV13", failures)
    require(go_meta.get("query_history_engine") is True, "dustui_go metadata must mark query_history_engine", failures)
    require("jobs" not in android and "workflows" not in android, "unsupported Devtool jobs/workflows must not be reintroduced", failures)

    for rel in [
        "go/internal/query/query.go",
        "go/internal/query/query_test.go",
        "go/internal/state/schema.go",
    ]:
        require((ROOT / rel).is_file(), f"OV04 query file missing: {rel}", failures)

    query_text = read("go/internal/query/query.go")
    for token in [
        "MaxPageLimit",
        "ErrStaleGeneration",
        "ErrCancelled",
        "Children(",
        "CurrentFolder(",
        "Search(",
        "LargeFiles(",
        "SummaryFor(",
        "Composition(",
        "GrowthSeries(",
        "PackageLinkedPaths(",
        "ActivityPage(",
        "deterministic_offset",
    ]:
        if token == "deterministic_offset":
            require("encodeOffset" in query_text and "decodeOffset" in query_text, "query package missing deterministic offset tokens", failures)
        else:
            require(token in query_text, f"query contract token missing: {token}", failures)

    test_text = read("go/internal/query/query_test.go")
    for token in [
        "TestPaginationIsGenerationBoundedAndStable",
        "TestSearchAndCurrentFolderAreDistinct",
        "TestSummaryCompositionHistoryAndLargeFilesUseExplicitSizeSemantics",
        "TestPackageLookupActivityAndCancellation",
        "ErrStaleGeneration",
        "ErrCancelled",
    ]:
        require(token in test_text, f"query behavioral test missing: {token}", failures)

    schema_text = read("go/internal/state/schema.go")
    for token in [
        "QueryIndexes",
        "idx_scan_entries_parent_page",
        "idx_scan_entries_search_name",
        "idx_scan_entries_logical_size",
        "idx_scan_entries_allocated_size",
        "idx_scan_entries_modified",
        "idx_scan_sessions_history",
    ]:
        require(token in schema_text, f"query schema/index token missing: {token}", failures)

    contract = run_json(["go", "run", "./cmd/dustui-go", "query-contract"])
    require(contract.get("phase") == "OV04", "query-contract phase must be OV04", failures)
    require(contract.get("pagination") == "deterministic_offset_token_bound_to_generation", "query pagination contract missing", failures)
    require(contract.get("maxPageLimit") == 500, "query max page limit must be bounded at 500", failures)
    for feature in [
        "paged_explorer_children",
        "whole_scan_search",
        "current_folder_filtering_distinct_from_search",
        "large_files",
        "storage_summary",
        "composition_treemap_aggregates",
        "scan_history_growth_series",
        "package_linked_path_lookup",
        "activity_page_preparation",
        "query_cancellation",
        "bounded_response_size",
    ]:
        require(feature in contract.get("features", []), f"query-contract feature missing: {feature}", failures)

    for rel in [
        "migration/contracts/v1/authority_transition_ov04.md",
        "migration/contracts/v1/legacy_paths_ov04.md",
        "migration/contracts/v1/query_search_history_ov04.md",
        "migration/contracts/v1/source_token_retirement_ov04.md",
        "migration/audit/v1/ov04_audit_loop.md",
        "migration/ledger/ov04_status.json",
    ]:
        require((ROOT / rel).is_file(), f"OV04 contract missing: {rel}", failures)

    findings = json.loads(read("migration/ledger/findings.json"))
    require(findings.get("phase") == "OV04", "migration ledger phase must advance to OV04", failures)
    by_id = {item.get("id"): item for item in findings.get("findings", [])}
    for fid in ["GO06", "RC-07", "RC-12", "ABH-06", "ABH-07", "ABH-08", "ABH-09", "ABH-12"]:
        require(fid in by_id and "OV04" in by_id[fid].get("overlays", []), f"OV04 ledger mapping missing: {fid}", failures)

    if audit_loop:
        combined = "\n".join(read(rel) for rel in [
            "migration/contracts/v1/authority_transition_ov04.md",
            "migration/contracts/v1/legacy_paths_ov04.md",
            "migration/contracts/v1/query_search_history_ov04.md",
            "migration/contracts/v1/source_token_retirement_ov04.md",
            "migration/audit/v1/ov04_audit_loop.md",
        ])
        for phrase in [
            "intentionally-suspended",
            "No Room/Go dual writer",
            "read-only",
            "search and current-folder filtering",
            "No hidden Python/C++ fallback",
            "Devtool target-local EXO jobs/workflows remain deferred",
        ]:
            require(phrase in combined, f"OV04 audit loop missing: {phrase}", failures)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV04 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV04 query/search/history contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
