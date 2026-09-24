#!/usr/bin/env python3
"""Audit actual DustUI OV14 retirement truth.

Default mode verifies that the repository ledger matches what production source
currently wires.  --gate additionally requires every cutover blocker to be gone.
This intentionally separates "the audit is truthful" from "Gate D is closed".
"""
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUS_PATH = ROOT / "migration/ledger/ov14_legacy_retirement_status.json"

DESIRED = {
    "cpp_scanner_runtime": "none",
    "python_runtime": "none",
    "room_domain_authority": "none",
    "legacy_destructive_executor": "none",
    "legacy_navigation": "none",
    "go_authority": "product-default-qualified",
    "android_go_runtime_binding": "production",
    "go_sqlite_persistence": "go-owned-sqlite",
    "generic_release_truth": "devtool-only",
}


def text(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def observe() -> tuple[dict[str, str], list[str]]:
    gradle = text("android/app/build.gradle.kts")
    scanner = text("android/app/src/main/kotlin/com/mikeyphw/dustui/root/NativeDustScanner.kt")
    root_service = text("android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
    container = text("android/app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt")
    app = text("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    if not app:
        app = text("android/app/src/main/kotlin/com/mikeyphw/dustui/DustUiApp.kt")
    devtool_text = text(".devtool.toml")
    go_mod = text("go/go.mod")

    go_android_sources = "\n".join(
        p.read_text(encoding="utf-8", errors="replace")
        for p in (ROOT / "android/app/src/main/kotlin/com/mikeyphw/dustui/go").glob("*.kt")
        if p.is_file()
    )
    state_sources = "\n".join(
        p.read_text(encoding="utf-8", errors="replace")
        for p in (ROOT / "go/internal/state").glob("*.go")
        if p.is_file()
    )

    cpp_active = (
        'System.loadLibrary("dustui_scanner")' in scanner
        and "NativeDustScanner.scan(" in root_service
        and "externalNativeBuild" in gradle
        and "cmake" in gradle
    )
    destructive_active = "NativeDustScanner.deleteReviewedPath(" in root_service
    room_active = (
        "Room.databaseBuilder" in container
        and "DustDatabase" in container
        and "ScanRepository" in container
    )
    python_target_present = "[targets.dust_tui_legacy]" in devtool_text

    # The OV05 runtime contract requires a fixed child-process path.  A Kotlin
    # data contract alone is not a production binding; there must be a process
    # launch implementation and Android packaging/build evidence for the Go ELF.
    process_binding = "ProcessBuilder(" in go_android_sources and "libdustui_go.so" in go_android_sources
    go_packaged = "libdustui_go.so" in gradle and ("go build" in gradle or "Exec" in gradle or "jniLibs" in gradle)
    go_binding = process_binding and go_packaged

    sqlite_code = ("database/sql" in state_sources and ("sql.Open(" in state_sources or "sql.OpenDB(" in state_sources))
    sqlite_driver = any(token in go_mod for token in ["modernc.org/sqlite", "github.com/mattn/go-sqlite3", "github.com/ncruces/go-sqlite3"])
    go_sqlite = sqlite_code and sqlite_driver

    release_scripts = [
        "android/scripts/release-gate.sh",
        "android/scripts/final-rc-gate.sh",
        "android/scripts/package-rc.sh",
        "android/scripts/install-smoke.sh",
        "android/scripts/device-certify.sh",
    ]
    release_kotlin = [
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleasePackaging.kt",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleaseReadiness.kt",
    ]
    generic_release_duplication = any((ROOT / rel).is_file() for rel in release_scripts) and any((ROOT / rel).is_file() for rel in release_kotlin)

    # OV14's legacy navigation blocker specifically tracks the superseded
    # DustNavigationModel authority. The current DustShellNavigation model is
    # the post-cutover adaptive shell, not a legacy authority.
    legacy_navigation = "DustNavigationModel" in app

    observed = {
        "cpp_scanner_runtime": "active-production" if cpp_active else "none",
        "python_runtime": "compatibility-only-target-present" if python_target_present else "none",
        "room_domain_authority": "active-production" if room_active else "none",
        "legacy_destructive_executor": "active-production" if destructive_active else "none",
        "legacy_navigation": "not-proven-retired" if legacy_navigation else "none",
        "go_authority": "go-domain-contracts-present-android-production-unqualified" if (not go_binding or not go_sqlite or room_active) else "product-default-qualified",
        "android_go_runtime_binding": "missing-production-binding" if not go_binding else "production",
        "go_sqlite_persistence": "missing-implementation" if not go_sqlite else "go-owned-sqlite",
        "generic_release_truth": "duplicated-app-local" if generic_release_duplication else "devtool-only",
    }

    blockers: list[str] = []
    mapping = [
        (observed["cpp_scanner_runtime"] != "none", "cpp_scanner_production_active"),
        (observed["python_runtime"] != "none", "python_legacy_target_present"),
        (observed["room_domain_authority"] != "none", "room_domain_authority_active"),
        (observed["legacy_destructive_executor"] != "none", "legacy_destructive_executor_active"),
        (observed["legacy_navigation"] != "none", "legacy_navigation_not_proven_retired"),
        (observed["android_go_runtime_binding"] != "production", "go_android_runtime_binding_missing"),
        (observed["go_sqlite_persistence"] != "go-owned-sqlite", "go_sqlite_persistence_missing"),
        (observed["generic_release_truth"] != "devtool-only", "generic_release_truth_duplicated"),
    ]
    blockers.extend(name for condition, name in mapping if condition)
    return observed, sorted(blockers)


def validate_config(failures: list[str]) -> None:
    try:
        cfg = tomllib.loads(text(".devtool.toml"))
    except Exception as exc:
        failures.append(f"invalid .devtool.toml: {exc}")
        return
    android = cfg.get("targets", {}).get("dustui_android", {})
    jobs = android.get("jobs", {})
    workflows = android.get("workflows", {})
    wrapper = cfg.get("wrapper", {})
    commands = wrapper.get("commands", {})
    if "retirement_audit" not in jobs:
        failures.append("dustui_android job retirement_audit missing")
    if "retirement_gate" not in jobs:
        failures.append("dustui_android job retirement_gate missing")
    if workflows.get("cutover_audit") != ["job:retirement_audit"]:
        failures.append("cutover_audit workflow must contain only job:retirement_audit")
    for name in ["gate_d", "validate"]:
        flow = workflows.get(name, [])
        if not flow or flow[0] != "job:retirement_gate":
            failures.append(f"{name} must begin with job:retirement_gate")
    if commands.get("cutover-audit", {}).get("workflow") != "cutover_audit":
        failures.append("wrapper cutover-audit command missing or miswired")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gate", action="store_true", help="fail unless all observed blockers are gone")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    observed, blockers = observe()
    failures: list[str] = []
    validate_config(failures)

    try:
        status = json.loads(text("migration/ledger/ov14_legacy_retirement_status.json"))
    except Exception as exc:
        status = {}
        failures.append(f"invalid OV14 status JSON: {exc}")

    expected_status = "closed" if not blockers else "reopened_blocked"
    expected_truth = "closed" if not blockers else "reopened"
    if status.get("status") != expected_status:
        failures.append(f"OV14 status must be {expected_status} for the observed blocker state")
    if status.get("gate_d_truth") != expected_truth:
        failures.append(f"OV14 status must record gate_d_truth={expected_truth}")
    if status.get("desired") != DESIRED:
        failures.append("OV14 desired-state ledger does not match the retirement target")
    if status.get("observed") != observed:
        failures.append("OV14 observed-state ledger is stale relative to production source")
    if sorted(status.get("blockers", [])) != blockers:
        failures.append("OV14 blocker ledger is stale relative to production source")

    payload = {
        "phase": "OV14",
        "status": "truth-audit-pass" if not failures else "truth-audit-fail",
        "gate": "blocked" if blockers else "ready",
        "observed": observed,
        "desired": DESIRED,
        "blockers": blockers,
        "contract_failures": failures,
    }

    if args.json:
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        for failure in failures:
            print(f"OV14 retirement truth failure: {failure}", file=sys.stderr)

    if failures:
        return 1
    if args.gate and blockers:
        if not args.json:
            print("Gate D retirement gate BLOCKED:", file=sys.stderr)
            for blocker in blockers:
                print(f"- {blocker}", file=sys.stderr)
        return 1
    if not args.json:
        if blockers:
            print(f"OV14 retirement truth audit PASS ({len(blockers)} blockers correctly recorded; Gate D remains open)")
        else:
            print("OV14 retirement truth audit PASS (no blockers; Gate D retirement condition ready)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
