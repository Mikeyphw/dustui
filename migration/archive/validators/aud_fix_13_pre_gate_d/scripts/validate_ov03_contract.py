#!/usr/bin/env python3
"""Validate DustUI OV03 Gate A scanner/ScanAuthority contracts."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
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
    android = devtool.get("targets", {}).get("dustui_android", {})
    go_target = devtool.get("targets", {}).get("dustui_go", {})
    android_meta = android.get("metadata", {})
    go_meta = go_target.get("metadata", {})
    require(android_meta.get("migration_phase") == "OV03", "dustui_android migration_phase must be OV03", failures)
    require(go_meta.get("migration_phase") == "OV03", "dustui_go migration_phase must be OV03", failures)
    require(android_meta.get("android_buildability") == "intentionally-suspended", "Android buildability suspension must be explicit", failures)
    require(android_meta.get("android_buildability_suspended_until") == "OV13", "Android suspension end gate must be OV13", failures)
    require("jobs" not in android and "workflows" not in android, "unsupported Devtool jobs/workflows must not be reintroduced", failures)

    for rel in [
        "go/internal/scan/scan.go",
        "go/internal/scan/authority.go",
        "go/internal/scan/scan_test.go",
    ]:
        require((ROOT / rel).is_file(), f"OV03 scanner file missing: {rel}", failures)
    scanner_text = read("go/internal/scan/scan.go") + read("go/internal/scan/authority.go")
    for token in [
        "CanonicalRoot", "Generation", "BackendIdentity", "OptionsFingerprint", "HiddenPolicy",
        "ExclusionPolicy", "FilesystemBoundaryPolicy", "ObservedEntryCount", "PersistedEntryCount",
        "MalformedRecordCount", "Truncated", "Integrity", "SourceCompleteness", "StaleAfterCleanup",
        "logicalSizeBytes", "allocatedSizeBytes", "sizeSemanticsUsedForScan", "CanAnalyze",
        "CanCompareWith", "CanDriveDeletion", "CanDriveDuplicateHashing", "CanDriveResidue",
        "CanExportCompleteData", "CanShowAsFresh", "CompatibilityWith",
    ]:
        require(token in scanner_text, f"scanner authority token missing: {token}", failures)

    contract = run_json(["go", "run", "./cmd/dustui-go", "scan-contract"])
    require("logical_and_allocated" in contract.get("sizeSemantics", []), "scan contract missing logical_and_allocated semantics", failures)
    for capability in ["canAnalyze", "canCompareWith", "canDriveDeletion", "canDriveDuplicateHashing", "canDriveResidue", "canExportCompleteData", "canShowAsFresh"]:
        require(capability in contract.get("authorityCapabilities", []), f"scan authority capability missing: {capability}", failures)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "a.txt").write_text("hello", encoding="utf-8")
        scan = run_json(["go", "run", "./cmd/dustui-go", "scan", str(root)])
        require(scan.get("canonicalRoot"), "scan output missing canonicalRoot", failures)
        require(scan.get("optionsFingerprint"), "scan output missing optionsFingerprint", failures)
        require(scan.get("integrity") == "complete", "simple scan should be complete", failures)
        require(scan.get("sourceCompleteness") == "complete", "simple scan should be source-complete", failures)
        require(scan.get("sizeSemanticsUsedForScan") == "logical_and_allocated", "scan output missing size semantic", failures)
        require(scan.get("observedEntryCount") == scan.get("persistedEntryCount"), "observed/persisted counts should match for simple scan", failures)

    for rel in [
        "migration/contracts/v1/android_buildability_suspension_ov03.md",
        "migration/contracts/v1/authority_transition_ov03.md",
        "migration/contracts/v1/legacy_paths_ov03.md",
        "migration/contracts/v1/source_token_retirement_ov03.md",
        "migration/audit/v1/ov03_gateA_audit_loop.md",
        "migration/ledger/ov03_gateA_status.json",
    ]:
        require((ROOT / rel).is_file(), f"OV03 gate contract missing: {rel}", failures)

    findings = json.loads(read("migration/ledger/findings.json"))
    require(findings.get("phase") == "OV03", "migration ledger phase must advance to OV03", failures)
    by_id = {item.get("id"): item for item in findings.get("findings", [])}
    for fid in ["GO04", "GO05", "RC-01", "RC-02", "RC-12", "ABH-03", "ABH-05", "ABH-07", "ABH-08"]:
        require(fid in by_id and "OV03" in by_id[fid].get("overlays", []), f"OV03 ledger mapping missing: {fid}", failures)

    if audit_loop:
        combined = "\n".join(read(rel) for rel in [
            "migration/contracts/v1/android_buildability_suspension_ov03.md",
            "migration/contracts/v1/authority_transition_ov03.md",
            "migration/contracts/v1/legacy_paths_ov03.md",
            "migration/contracts/v1/source_token_retirement_ov03.md",
            "migration/audit/v1/ov03_gateA_audit_loop.md",
        ])
        for phrase in [
            "intentionally-suspended",
            "No Room/Go dual writer",
            "No legacy destructive executor becomes newly reachable",
            "semantic",
            "Devtool target-local EXO jobs/workflows remain deferred",
        ]:
            require(phrase in combined, f"OV03 audit loop missing: {phrase}", failures)
    return failures

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV03 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV03 Gate A scanner/ScanAuthority contract passed")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
