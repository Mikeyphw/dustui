#!/usr/bin/env python3
"""Read-only AUD-FIX-16 post-Gate-D physical-retirement contract."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path

from audfix16_retirement import source_projection_sha256

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def load(rel: str) -> dict:
    p = ROOT / rel
    return json.loads(p.read_text(encoding="utf-8")) if p.is_file() else {}


def has_live_payload(rel: str) -> bool:
    """True when a retired tree still contains a file or symlink.

    Devtool/Git remove tracked files but intentionally do not model empty
    directory deletion. Empty directory shells therefore are not live
    authority and must not fail physical-retirement qualification.
    """
    root = ROOT / rel
    if root.is_file() or root.is_symlink():
        return True
    if not root.is_dir():
        return False
    return any(p.is_file() or p.is_symlink() for p in root.rglob("*"))


def audit() -> list[str]:
    failures: list[str] = []
    def req(ok: bool, msg: str) -> None:
        if not ok:
            failures.append(msg)

    state = load("migration/qualification/v1/current_state.json")
    record = load("migration/qualification/v1/ov14_retirement.json")
    bundle = load("migration/qualification/v1/gate_d_bundle.json")
    gate = state.get("gate_d", {})
    ov14 = state.get("ov14", {})
    obs = state.get("observed_repository", {})
    req(gate.get("qualified") is True and gate.get("status") == "qualified", "Gate-D qualified ancestor was not preserved")
    req(ov14.get("status") == "physically-retired" and ov14.get("retirement_complete") is True, "OV14 is not physically retired")
    req(state.get("gate_e", {}).get("status") in {"eligible-for-qualification", "qualified"}, "Gate E is not eligible after physical retirement")
    req(record.get("status") == "pass" and record.get("gate_d_resealed") is False, "retirement record missing/pass or incorrectly reseals Gate D")
    req(record.get("gate_d_ancestor_source_sha256") == gate.get("passing_source_tree_sha256"), "retirement record Gate-D ancestor drift")
    if bundle.get("source_snapshot_sha256"):
        req(bundle.get("source_snapshot_sha256") == gate.get("passing_source_tree_sha256"), "Gate-D bundle ancestor drift")
    req(record.get("post_retirement_source_sha256") == source_projection_sha256(), "post-retirement source projection is stale")

    for key in ["python_textual_oracle_present", "room_artifacts_present", "ndjson_lifecycle_present"]:
        req(obs.get(key) is False, f"canonical observation still reports retired surface: {key}")
    req(not obs.get("generic_tooling_duplication_paths"), "generic app-local tooling authority remains")
    req(not obs.get("release_kotlin_authority_paths"), "release Kotlin authority remains")
    req(not obs.get("predecessor_source_token_paths"), "predecessor source-token tests remain live")
    req(obs.get("go_sqlite_implementation_present") is True, "Go SQLite successor authority missing")
    req(obs.get("android_go_process_binding_present") is True and obs.get("android_go_packaging_present") is True, "Android Go successor binding missing")

    req(not has_live_payload("migration/oracle/python_textual"), "live Python/Textual oracle payload still exists")
    oracle_archive = ROOT / "migration/archive/oracles/aud_fix_16_python_textual"
    req((oracle_archive / "AUD_FIX_16_ARCHIVE.md").is_file(), "Python/Textual archive provenance missing")
    req("non-authoritative" in read("migration/archive/oracles/aud_fix_16_python_textual/AUD_FIX_16_ARCHIVE.md").lower(), "oracle archive is not explicitly non-authoritative")

    room_tokens = ["androidx.room", "RoomDatabase", "@Dao", "@Entity", "libs.androidx.room", "libs.plugins.ksp"]
    live_android = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in (ROOT / "android/app/src/main").rglob("*.kt")) + read("android/app/build.gradle.kts")
    for token in room_tokens:
        req(token not in live_android, f"Room/KSP production token remains: {token}")
    for rel in [
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/DustDatabase.kt",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt",
        "android/app/src/main/kotlin/com/mikeyphw/dustui/data/LegacyRoomV3Importer.kt",
        "android/app/schemas",
    ]:
        req(not has_live_payload(rel), f"retired Room surface remains live: {rel}")

    scan_service = read("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt")
    req("ParcelFileDescriptor.createPipe()" in scan_service and "ScanEventStreamReader" in scan_service, "scan stream is not direct-pipe based")
    req("scan-streams" not in scan_service and ".ndjson" not in scan_service and "NdjsonTailReader" not in live_android, "NDJSON file-tail lifecycle remains in production")
    req((ROOT / "android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanEventStreamReader.kt").is_file(), "direct scan-event stream reader missing")

    retired_scripts = [
        "device-certify.sh", "install-smoke.sh", "run-device-scale-performance.sh",
        "run-multi-user-residue-validation.sh", "run-rooted-destructive-safety-tests.sh",
        "release-gate.sh", "package-rc.sh", "final-rc-gate.sh", "validate-remediation-full.sh", "validate-room-migrations.sh",
    ]
    for name in retired_scripts:
        req(not (ROOT / "android/scripts" / name).exists(), f"generic predecessor helper remains live: {name}")
    for p in sorted((ROOT / "android/scripts").glob("*")):
        if not p.is_file():
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        if re.search(r"(?m)(?:^|[;|&()\s])adb\s+", text):
            failures.append(f"raw adb remains in live Android helper: {p.relative_to(ROOT)}")

    retired_tests = [
        "android/app/src/test/kotlin/com/mikeyphw/dustui/automation/AudFix04AutomationContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/diagnostics/AudFix05DiagnosticsContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/go/DomainAuthorityProductionReachabilityContractTest.kt",
        "android/app/src/test/kotlin/com/mikeyphw/dustui/go/GoRuntimeProductionBindingContractTest.kt",
    ]
    for rel in retired_tests:
        req(not (ROOT / rel).exists(), f"predecessor source-token guard remains live: {rel}")
        req((ROOT / "migration/archive/validators/aud_fix_16_post_gate_d" / rel).is_file(), f"retired validator archive missing: {rel}")
    req((ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/go/DomainAuthorityGoldenContractTest.kt").is_file(), "semantic golden fixture contract was incorrectly retired")

    cfg = tomllib.loads(read(".devtool.toml"))
    android = cfg.get("targets", {}).get("dustui_android", {})
    req("aud_fix_16" in android.get("workflows", {}), "AUD-FIX-16 Devtool workflow missing")
    req("audfix16_retirement_contract" in android.get("jobs", {}), "AUD-FIX-16 contract job missing")
    wrapper = cfg.get("wrapper", {}).get("commands", {})
    req(wrapper.get("aud-fix-16", {}).get("workflow") == "aud_fix_16", "aud-fix-16 wrapper command missing/misbound")
    req("migration/oracle/python_textual" not in read(".devtool.toml"), "Devtool config still names the live oracle path")

    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    args = ap.parse_args()
    failures = audit()
    if args.audit_loop and not failures:
        failures = [f"second-pass:{x}" for x in audit()]
    if failures:
        for item in failures:
            print("AUD-FIX-16 contract failure:", item, file=sys.stderr)
        return 1
    print("AUD-FIX-16 physical retirement contract PASS; Gate-D ancestor preserved; Gate E eligible")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
