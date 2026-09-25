#!/usr/bin/env python3
"""Validate DustUI OV01 migration contracts without doing full Android validation."""
from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def load_json(rel: str) -> dict:
    return json.loads(read(rel))


def validate(audit_loop: bool = False) -> list[str]:
    failures: list[str] = []

    devtool = tomllib.loads(read(".devtool.toml"))
    require(devtool.get("workspace", {}).get("default_target") == "dustui_android", "workspace.default_target must be dustui_android", failures)
    targets = devtool.get("targets", {})
    require("dustui_android" in targets, "dustui_android target missing", failures)
    require("dustui_go" in targets, "dustui_go target missing", failures)
    require("dust_tui_legacy" in targets, "dust_tui_legacy target missing", failures)
    require(targets["dustui_android"].get("gradle", {}).get("structured_events") is True, "Android Gradle structured_events must be true", failures)
    require(targets["dustui_android"].get("android", {}).get("aapt2_provider") == "auto", "aapt2 provider must return to auto", failures)
    require(targets["dustui_android"].get("gradle", {}).get("version") == "9.7.1", "Gradle baseline 9.7.1 must be preserved", failures)
    require(targets["dustui_android"].get("validation", {}).get("max_workers") == 2, "Termux worker tuning must be preserved", failures)
    release_plan = targets["dustui_android"].get("tasks", {}).get("release_plan", [])
    require(release_plan == [":app:testReleaseUnitTest", ":app:lintRelease", ":app:assembleRelease"], "release_plan must use Release tasks only", failures)
    require(all("Debug" not in item for item in release_plan), "release_plan must not contain Debug tasks", failures)
    jobs = targets["dustui_android"].get("jobs", {})
    for job_id in ["contracts", "go_validate", "android_test", "android_lint", "migration_audit"]:
        require(job_id in jobs, f"EXO job {job_id} missing", failures)
    require("workflows" in targets["dustui_android"], "EXO workflows missing", failures)

    findings = load_json("migration/ledger/findings.json")
    coverage = findings.get("coverage", {})
    require(coverage.get("ABH") == 16, "ABH coverage must be 16", failures)
    require(coverage.get("RC") == 14, "RC coverage must be 14", failures)
    require(coverage.get("N") == 8, "N coverage must be 8", failures)
    require(coverage.get("CP") == 2, "CP coverage must be 2", failures)
    ids = {item.get("id") for item in findings.get("findings", [])}
    for prefix, count in [("ABH", 16), ("RC", 14), ("N", 8), ("CP", 2)]:
        for index in range(1, count + 1):
            expected = f"{prefix}-{index:02d}"
            require(expected in ids, f"ledger missing {expected}", failures)

    inventory = load_json("migration/audit/v1/inventory.json")
    require(len(inventory.get("android_destinations", [])) >= 16, "destination inventory must include current 16 destinations", failures)
    require(inventory.get("destructive_entry_points"), "destructive entrypoint inventory missing", failures)
    require(inventory.get("raw_adb_callers"), "raw ADB caller inventory missing", failures)
    require(inventory.get("authoritative_file_publishers"), "file publisher inventory missing", failures)
    source_map = load_json("migration/ledger/source_token_contract_tests.json")
    require(source_map.get("tests"), "source/token retirement inventory missing", failures)

    root_service = read("android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
    require("newCachedThreadPool" not in root_service, "DustRootService must not use newCachedThreadPool", failures)
    require("ThreadPoolExecutor" in root_service and "MAX_PRIVILEGED_QUEUE_DEPTH" in root_service, "DustRootService bounded executor not present", failures)

    client = read("android/app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt")
    require("Reviewed cleanup plan publication failed" in client, "cleanup-plan publication must fail closed", failures)
    require("file.writeText(review.toExecutionJson()" not in client, "direct overwrite fallback must be removed", failures)

    repo = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    for token in ["cleanupScanBlockReason", "source scan is truncated", "scan persistence is stale", "different scan"]:
        require(token in repo, f"cleanup source containment missing token: {token}", failures)

    worker = read("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScheduledScanWorker.kt")
    require("STATUS_LAUNCHED_NOT_TERMINAL" in worker, "scheduled scan must label admitted launch as non-terminal", failures)
    require("KEY_SCAN_STATUS" in worker, "scheduled scan must publish status output data", failures)

    release_gate = read("android/scripts/release-gate.sh")
    package_rc = read("android/scripts/package-rc.sh")
    for rel, text in [("release-gate.sh", release_gate), ("package-rc.sh", package_rc)]:
        require("testReleaseUnitTest" in text and "lintRelease" in text and "assembleRelease" in text, f"{rel} must use Release tasks", failures)
        require("testDebugUnitTest" not in text and "lintDebug" not in text and "assembleDebug" not in text, f"{rel} release plan must not use Debug tasks", failures)

    app_build = read("android/app/build.gradle.kts")
    for token in ["compileSdk = 36", "targetSdk = 36", "buildToolsVersion = \"36.0.0\"", "jvmToolchain(21)"]:
        require(token in app_build, f"Android toolchain baseline changed: missing {token}", failures)

    go_mod = read("go/go.mod")
    readiness = read("go/internal/readiness/readiness.go")
    require("module github.com/mikeyphw/dustui/go" in go_mod, "Go module missing", failures)
    require("ProductionEngine:  false" in readiness and "SQLiteAuthority:   false" in readiness and "MutationAuthority: false" in readiness, "OV01 Go readiness must be non-authoritative", failures)

    for rel in [
        "migration/contracts/v1/authority_transition_ov01.md",
        "migration/contracts/v1/legacy_paths_ov01.md",
        "migration/contracts/v1/devtool_release_policy.md",
        "migration/contracts/v1/source_token_retirement.md",
        "migration/audit/v1/ov01_audit_loop.md",
    ]:
        require((ROOT / rel).is_file(), f"required migration contract missing: {rel}", failures)

    if audit_loop:
        combined = "\n".join([
            read("migration/contracts/v1/authority_transition_ov01.md"),
            read("migration/contracts/v1/legacy_paths_ov01.md"),
            read("migration/audit/v1/ov01_audit_loop.md"),
        ])
        for phrase in [
            "No Go/Room dual writer",
            "No Python/C++/Room/UI production subsystem is physically removed",
            "Root privileged admission is bounded",
            "Release plan no longer resolves to Debug tasks",
        ]:
            require(phrase in combined, f"audit loop missing: {phrase}", failures)

    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(audit_loop=args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV01 contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV01 migration contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
