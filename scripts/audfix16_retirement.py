#!/usr/bin/env python3
"""Finalize AUD-FIX-16 physical retirement without resealing Gate D."""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

from audfix01_core import compute_qualification, load_json, sha256_file

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "migration/qualification/v1/gate_d_evidence.json"
BUNDLE = ROOT / "migration/qualification/v1/gate_d_bundle.json"
RETIREMENT = ROOT / "migration/qualification/v1/ov14_retirement.json"

EXCLUDED_EXACT = {
    # Derived qualification/acceptance/ledger projections are intentionally
    # outside the post-retirement source identity. They are refreshed from
    # source/evidence during finalization and must not make the source hash
    # self-referential or order-dependent.
    "migration/qualification/v1/current_state.json",
    "migration/qualification/v1/ov14_retirement.json",
    "migration/acceptance/v1/current_state.json",
    "migration/ledger/ov14_legacy_retirement_status.json",
    "go/internal/qualification/state_generated.go",
    "go/internal/qualification/acceptance_generated.go",
}
EXCLUDED_PREFIXES = (
    ".git/",
    ".devtool/",
    "build/",
    "android/.gradle/",
    "android/app/build/",
    "go/build/",
)


AUD16_ADDED_ROOTS = (
    "migration/archive/oracles/aud_fix_16_python_textual",
    "migration/archive/room/aud_fix_16_pre_retirement",
    "migration/archive/validators/aud_fix_16_post_gate_d",
    "migration/archive/docs/aud_fix_16_predecessor",
)
AUD16_ADDED_FILES = (
    "android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanEventStreamReader.kt",
    "android/app/src/test/kotlin/com/mikeyphw/dustui/scan/ScanEventStreamReaderTest.kt",
    "scripts/audfix16_retirement.py",
    "scripts/validate_audfix16_contract.py",
)


def candidate_paths() -> list[str]:
    # The retirement identity must describe repository source, not arbitrary
    # untracked developer files. Start with tracked paths and add only the
    # AUD-FIX-16 files that are intentionally new before the overlay commit.
    try:
        cp = subprocess.run(
            ["git", "ls-files", "--cached", "-z"],
            cwd=ROOT, capture_output=True, check=False, timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        cp = None
    if cp is not None and cp.returncode == 0:
        raw = cp.stdout.decode("utf-8", errors="surrogateescape")
        paths = {p for p in raw.split("\0") if p}
        for rel in AUD16_ADDED_FILES:
            if (ROOT / rel).is_file():
                paths.add(rel)
        for rel_root in AUD16_ADDED_ROOTS:
            base = ROOT / rel_root
            if base.is_dir():
                paths.update(
                    p.relative_to(ROOT).as_posix()
                    for p in base.rglob("*")
                    if p.is_file()
                )
        return sorted(paths)
    return sorted({p.relative_to(ROOT).as_posix() for p in ROOT.rglob("*") if p.is_file()})


def included(rel: str) -> bool:
    parts = Path(rel).parts
    if "__pycache__" in parts or rel.endswith((".pyc", ".pyo")) or ".bak-" in rel:
        return False
    return rel not in EXCLUDED_EXACT and not any(rel == p.rstrip("/") or rel.startswith(p) for p in EXCLUDED_PREFIXES)


def source_projection_sha256() -> str:
    h = hashlib.sha256()
    for rel in candidate_paths():
        if not included(rel):
            continue
        path = ROOT / rel
        if not path.is_file() or path.is_symlink():
            continue
        digest = sha256_file(path)
        h.update(f"F\0{rel}\0{digest}\n".encode())
    return h.hexdigest()


def build_record() -> dict:
    registry = load_json(REGISTRY)
    state = compute_qualification(ROOT, registry)
    gate = state["gate_d"]
    if not gate.get("qualified"):
        raise RuntimeError("AUD-FIX-16 requires the already-qualified AUD-FIX-15 Gate-D ancestor")
    blockers = list(state.get("ov14", {}).get("retirement_blockers") or [])
    if blockers:
        raise RuntimeError("physical retirement is incomplete: " + ", ".join(blockers))
    bundle = load_json(BUNDLE) if BUNDLE.is_file() else {}
    ancestor = gate.get("passing_source_tree_sha256")
    bundle_ancestor = bundle.get("source_snapshot_sha256")
    if bundle_ancestor and ancestor and bundle_ancestor != ancestor:
        raise RuntimeError("Gate-D bundle ancestor does not match canonical qualified source binding")
    archived = [
        "migration/archive/oracles/aud_fix_16_python_textual",
        "migration/archive/room/aud_fix_16_pre_retirement",
        "migration/archive/validators/aud_fix_16_post_gate_d",
        "migration/archive/docs/aud_fix_16_predecessor",
    ]
    return {
        "schema": "dustui.aud-fix-16.retirement.v1",
        "status": "pass",
        "generated_by": "scripts/audfix16_retirement.py",
        "gate_d_resealed": False,
        "gate_d_ancestor_source_sha256": ancestor,
        "gate_d_bundle": "migration/qualification/v1/gate_d_bundle.json",
        "gate_d_bundle_sha256": sha256_file(BUNDLE) if BUNDLE.is_file() else None,
        "post_retirement_source_sha256": source_projection_sha256(),
        "archived_non_authoritative_paths": archived,
        "retired_surfaces": {
            "python_textual_oracle": True,
            "room_android_integration": True,
            "ndjson_file_tail_state_transfer": True,
            "generic_android_release_device_helpers": True,
            "predecessor_source_token_guards": True,
        },
        "next_gate": "Gate E",
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("action", choices=["check", "finalize"], nargs="?", default="check")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    expected = build_record()
    text = json.dumps(expected, indent=2, sort_keys=True) + "\n"
    if args.action == "check":
        if not RETIREMENT.is_file() or RETIREMENT.read_text(encoding="utf-8") != text:
            print("AUD-FIX-16 retirement evidence is missing or stale")
            return 1
    else:
        RETIREMENT.parent.mkdir(parents=True, exist_ok=True)
        RETIREMENT.write_text(text, encoding="utf-8")
        refresh_commands = [
            ["python3", "scripts/refresh_qualification_state.py"],
            ["python3", "scripts/refresh_ov14_retirement_observation.py"],
            ["python3", "scripts/refresh_acceptance_state.py"],
        ]
        for command in refresh_commands:
            cp = subprocess.run(command, cwd=ROOT, check=False)
            if cp.returncode != 0:
                return cp.returncode
    if args.json:
        print(text, end="")
    else:
        print("AUD-FIX-16 physical retirement evidence PASS; Gate D ancestor preserved; Gate E is next")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
