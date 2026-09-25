#!/usr/bin/env python3
"""Successor-aware validation of historical OV01 invariants.

OV01 established the migration boundary. Later AUD-FIX stages are allowed to
retire its temporary host/target declarations; this checker therefore validates
surviving safety/toolchain invariants plus the canonical successor relation
instead of demanding obsolete OV01 source tokens.
"""
from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8", errors="replace")


def require(ok: bool, message: str, failures: list[str]) -> None:
    if not ok:
        failures.append(message)


def validate(audit_loop: bool = False) -> list[str]:
    failures: list[str] = []
    cfg = tomllib.loads(read(".devtool.toml"))
    targets = cfg.get("targets", {})
    require(cfg.get("workspace", {}).get("default_target") == "dustui_android", "workspace.default_target must remain dustui_android", failures)
    require("dustui_android" in targets and "dustui_go" in targets, "Android/Go successor targets missing", failures)
    android = targets.get("dustui_android", {})
    go = targets.get("dustui_go", {})
    require(android.get("gradle", {}).get("version") == "9.7.1", "Gradle baseline 9.7.1 changed", failures)
    require(android.get("android", {}).get("aapt2_provider") == "auto", "aapt2 provider must remain auto", failures)
    require(android.get("validation", {}).get("max_workers") == 2, "Termux worker tuning changed", failures)
    require(android.get("tasks", {}).get("release_plan") == [":app:testReleaseUnitTest", ":app:lintRelease", ":app:assembleRelease"], "release plan must remain release-only", failures)

    gm = go.get("metadata", {})
    if gm.get("host_authority_default") is True:
        require("dust_tui_legacy" not in targets, "retired dust_tui_legacy target reappeared", failures)
        require(not (ROOT / "pyproject.toml").exists(), "root Python production project reappeared", failures)
        require((ROOT / "migration/oracle/python_textual/src/dust_tui").is_dir(), "post-cutover parity oracle missing", failures)
        readiness = read("go/internal/readiness/readiness.go")
        for token in ["ProductionEngine:        true", "GoCLIDefault:            true", "GoTUIDefault:            true"]:
            require(token in readiness, f"AUD-FIX-07 successor readiness missing {token}", failures)
        generated = read("go/internal/qualification/state_generated.go")
        require("const LegacyPythonTextualPresent = false" in generated, "canonical state still exposes legacy Python production", failures)
        require("const PythonTextualOraclePresent = true" in generated, "canonical state lost parity oracle", failures)
    else:
        require("dust_tui_legacy" in targets, "pre-cutover OV01 compatibility target missing", failures)

    root_service = read("android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
    require("newCachedThreadPool" not in root_service and "ThreadPoolExecutor" in root_service and "MAX_PRIVILEGED_QUEUE_DEPTH" in root_service, "bounded root admission invariant regressed", failures)
    client = read("android/app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt")
    require("Reviewed cleanup plan publication failed" in client and "file.writeText(review.toExecutionJson()" not in client, "cleanup-plan publication no longer fails closed", failures)

    state = json.loads(read("migration/qualification/v1/current_state.json"))
    require(state.get("gate_d", {}).get("status") == "open" and state.get("gate_d", {}).get("qualified") is False, "historical OV01 validator must not invent Gate D closure", failures)

    for rel in [
        "migration/contracts/v1/authority_transition_ov01.md",
        "migration/contracts/v1/legacy_paths_ov01.md",
        "migration/contracts/v1/devtool_release_policy.md",
        "migration/contracts/v1/source_token_retirement.md",
        "migration/audit/v1/ov01_audit_loop.md",
    ]:
        require((ROOT / rel).is_file(), f"historical OV01 evidence missing: {rel}", failures)

    if audit_loop and not failures:
        # Deterministic second evaluation proves the leaf is read-only.
        return validate(False)
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-loop", action="store_true")
    args = parser.parse_args()
    failures = validate(args.audit_loop)
    if failures:
        for item in failures:
            print(f"OV01 successor-aware contract failure: {item}", file=sys.stderr)
        return 1
    print("DustUI OV01 historical invariants PASS (successor-aware)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
