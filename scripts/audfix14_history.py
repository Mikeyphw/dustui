#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "migration/qualification/v1/historical_gate_evidence.json"
DEFAULT_REPORT = ROOT / "build/evidence/aud-fix/14/historical_discovery.json"
WORKTREE_ROOT = ROOT / ".devtool/historical"
LEGACY_PYTHON = ROOT / "src/dust_tui"
DEVTOOL_CONFIG = ROOT / ".devtool.toml"
ORACLE_LEGACY_PYTHON = ROOT / "migration/oracle/python_textual/src/dust_tui"
EXPECTED_ORACLE_SOURCE_SHA256 = "3255c9fad5be02656c5efdc660f433f2b363b587109a2713b556530d370b1a8e"


def _run(*args: str, cwd: Path = ROOT, text: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run([*args], cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=text, timeout=60)


def _git_bytes(commit: str, rel: str) -> bytes | None:
    try:
        cp = subprocess.run(["git", "show", f"{commit}:{rel}"], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20)
    except Exception:
        return None
    return cp.stdout if cp.returncode == 0 else None


def _tree(commit: str) -> str | None:
    cp = _run("git", "rev-parse", f"{commit}^{{tree}}")
    return cp.stdout.strip() if cp.returncode == 0 else None


def _subject(commit: str) -> str:
    cp = _run("git", "show", "-s", "--format=%s", commit)
    return cp.stdout.strip() if cp.returncode == 0 else ""


def _sha256(path: Path) -> str | None:
    if not path.is_file():
        return None
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _tree_fingerprint(path: Path) -> dict[str, Any]:
    """Return a stable content fingerprint without following external symlinks."""
    if not path.exists() and not path.is_symlink():
        return {"exists": False, "kind": "missing", "sha256": None, "entries": 0}
    if path.is_symlink():
        payload = f"L\0{path.readlink()}".encode()
        return {"exists": True, "kind": "symlink", "sha256": hashlib.sha256(payload).hexdigest(), "entries": 1}
    if path.is_file():
        return {"exists": True, "kind": "file", "sha256": _sha256(path), "entries": 1}
    if not path.is_dir():
        return {"exists": True, "kind": "other", "sha256": None, "entries": 1}

    rows: list[bytes] = []
    entries = 0
    for child in sorted(path.rglob("*"), key=lambda p: p.as_posix()):
        rel = child.relative_to(path).as_posix()
        if child.is_symlink():
            rows.append(f"L\0{rel}\0{child.readlink()}\n".encode())
            entries += 1
        elif child.is_file():
            rows.append(f"F\0{rel}\0{_sha256(child)}\n".encode())
            entries += 1
    h = hashlib.sha256()
    for row in rows:
        h.update(row)
    return {"exists": True, "kind": "directory", "sha256": h.hexdigest(), "entries": entries}


def _live_tree_baseline() -> dict[str, Any]:
    return {
        "legacy_python": _tree_fingerprint(LEGACY_PYTHON),
        "devtool_config_sha256": _sha256(DEVTOOL_CONFIG),
    }


def _status_for_legacy_python() -> list[str]:
    cp = _run("git", "status", "--porcelain=v1", "--untracked-files=all", "--ignored=matching", "--", "src/dust_tui")
    if cp.returncode != 0:
        return [f"git-status-error:{cp.stderr.strip() or cp.stdout.strip()}"]
    return [line for line in cp.stdout.splitlines() if line.strip()]


def _tracked_legacy_python() -> list[str]:
    cp = _run("git", "ls-files", "--", "src/dust_tui")
    if cp.returncode != 0:
        return [f"git-ls-files-error:{cp.stderr.strip() or cp.stdout.strip()}"]
    return [line for line in cp.stdout.splitlines() if line.strip()]


def _current_config_uses_legacy_python() -> bool:
    text = DEVTOOL_CONFIG.read_text(encoding="utf-8", errors="replace") if DEVTOOL_CONFIG.is_file() else ""
    return 'kind = "python"' in text and 'run_module = "dust_tui"' in text


def _remove_legacy_python_tree() -> None:
    if LEGACY_PYTHON.is_symlink() or LEGACY_PYTHON.is_file():
        LEGACY_PYTHON.unlink()
    elif LEGACY_PYTHON.is_dir():
        shutil.rmtree(LEGACY_PYTHON)
    src_parent = LEGACY_PYTHON.parent
    try:
        src_parent.rmdir()
    except OSError:
        pass


def _prepared_legacy_matches(prepared: list[str], observed: dict[str, Any]) -> list[str]:
    matches: list[str] = []
    if not observed.get("exists") or not observed.get("sha256"):
        return matches
    for rel in prepared:
        candidate = ROOT / rel / "src/dust_tui"
        fp = _tree_fingerprint(candidate)
        if fp.get("exists") and fp.get("kind") == observed.get("kind") and fp.get("sha256") == observed.get("sha256"):
            matches.append(str(candidate.relative_to(ROOT)))
    return matches


def _source_projection(path: Path) -> dict[str, Any]:
    """Fingerprint source files while rejecting generated-cache ambiguity.

    The retired Textual tree may have generated ``__pycache__/*.pyc`` files
    left behind by an earlier invocation. Those bytecode files are not source
    provenance and are intentionally excluded from the source projection, but
    *only* after every other path is proven safe. Symlinks, unexpected
    directories, and non-bytecode files under ``__pycache__`` fail closed.
    """
    result: dict[str, Any] = {
        "exists": path.is_dir() and not path.is_symlink(),
        "source_sha256": None,
        "source_entries": 0,
        "generated_cache_files": [],
        "unsafe_paths": [],
        "files": {},
    }
    if not result["exists"]:
        return result

    rows: list[bytes] = []
    files: dict[str, str] = {}
    cache_files: list[str] = []
    unsafe: list[str] = []
    for child in sorted(path.rglob("*"), key=lambda p: p.as_posix()):
        rel = child.relative_to(path).as_posix()
        if child.is_symlink():
            unsafe.append(rel)
            continue
        if child.is_dir():
            if rel != "__pycache__":
                unsafe.append(rel + "/")
            continue
        if not child.is_file():
            unsafe.append(rel)
            continue
        if rel.startswith("__pycache__/"):
            if child.suffix == ".pyc" and "/" not in rel[len("__pycache__/"):]:
                cache_files.append(rel)
            else:
                unsafe.append(rel)
            continue
        digest = _sha256(child)
        if digest is None:
            unsafe.append(rel)
            continue
        files[rel] = digest
        rows.append(f"F\0{rel}\0{digest}\n".encode())

    h = hashlib.sha256()
    for row in rows:
        h.update(row)
    result.update(
        source_sha256=h.hexdigest(),
        source_entries=len(files),
        generated_cache_files=cache_files,
        unsafe_paths=unsafe,
        files=files,
    )
    return result


def _oracle_legacy_proof() -> dict[str, Any]:
    live = _source_projection(LEGACY_PYTHON)
    oracle = _source_projection(ORACLE_LEGACY_PYTHON)
    oracle_stable = (
        oracle.get("exists")
        and not oracle.get("unsafe_paths")
        and not oracle.get("generated_cache_files")
        and oracle.get("source_sha256") == EXPECTED_ORACLE_SOURCE_SHA256
    )
    matches = (
        live.get("exists")
        and oracle_stable
        and not live.get("unsafe_paths")
        and live.get("source_sha256") == oracle.get("source_sha256")
        and live.get("files") == oracle.get("files")
    )
    return {
        "matches": bool(matches),
        "expected_oracle_source_sha256": EXPECTED_ORACLE_SOURCE_SHA256,
        "oracle_source_sha256": oracle.get("source_sha256"),
        "oracle_stable": bool(oracle_stable),
        "live_source_sha256": live.get("source_sha256"),
        "source_entries": live.get("source_entries"),
        "generated_cache_files": live.get("generated_cache_files", []),
        "unsafe_paths": live.get("unsafe_paths", []),
    }


def enforce_live_tree_isolation(report: dict[str, Any], baseline: dict[str, Any]) -> bool:
    """Protect current-tree qualification from historical-worktree residue.

    Historical replay must never make retired Python/Textual source look current.
    There are two safe cleanup cases:

    * transient residue created during this preparation run; and
    * preexisting residue left by an earlier failed preparation run.

    The preexisting case is intentionally stricter: the live `src/dust_tui`
    tree must be wholly untracked by Git, the current Devtool configuration
    must not select the Python runtime, and the complete content fingerprint
    must exactly match a prepared historical worktree. Any tracked, modified,
    configured, or nonhistorical tree is preserved and fails closed.
    """
    prepared = list(report.get("prepared_worktrees", []))
    before_legacy = baseline["legacy_python"]
    observed_legacy = _tree_fingerprint(LEGACY_PYTHON)
    before_devtool = baseline["devtool_config_sha256"]
    observed_devtool = _sha256(DEVTOOL_CONFIG)
    statuses = _status_for_legacy_python() if observed_legacy.get("exists") else []
    tracked = _tracked_legacy_python() if observed_legacy.get("exists") else []
    matches = _prepared_legacy_matches(prepared, observed_legacy)
    oracle_proof = _oracle_legacy_proof() if observed_legacy.get("exists") else {"matches": False}
    configured_current = _current_config_uses_legacy_python()
    isolation: dict[str, Any] = {
        "baseline": baseline,
        "observed_after_prepare": {
            "legacy_python": observed_legacy,
            "devtool_config_sha256": observed_devtool,
        },
        "git_status": statuses,
        "tracked_paths": tracked,
        "historical_matches": matches,
        "retired_oracle_proof": oracle_proof,
        "configured_as_current_python": configured_current,
        "action": "none",
        "status": "clean",
        "final_matches_baseline": False,
        "final_matches_expected": False,
    }
    expected_final = baseline

    if before_devtool != observed_devtool:
        isolation["status"] = "FAILED_LIVE_DEVTOOL_CONFIG_MUTATION"
        isolation["reason"] = ".devtool.toml changed while preparing historical worktrees"
    elif observed_legacy != before_legacy:
        baseline_absent = before_legacy.get("exists") is False
        wholly_untracked = not tracked and not any(x.startswith("git-ls-files-error:") for x in tracked)
        exact_historical_copy = bool(matches)
        exact_retired_oracle_copy = bool(oracle_proof.get("matches"))
        if baseline_absent and observed_legacy.get("exists") and wholly_untracked and (exact_historical_copy or exact_retired_oracle_copy) and not configured_current:
            _remove_legacy_python_tree()
            isolation["action"] = (
                "removed-untracked-exact-historical-copy"
                if exact_historical_copy
                else "removed-untracked-exact-retired-oracle-copy-with-generated-cache"
            )
            isolation["status"] = (
                "cleaned-transient-historical-copy"
                if exact_historical_copy
                else "cleaned-transient-retired-oracle-copy"
            )
        else:
            isolation["status"] = "FAILED_LIVE_TREE_CONTAMINATION"
            isolation["reason"] = (
                "current src/dust_tui changed during historical preparation but was not proven "
                "to be a newly-created wholly-untracked exact copy of a prepared historical worktree or the immutable retired oracle"
            )
    elif before_legacy.get("exists") and observed_legacy.get("exists") and not configured_current:
        wholly_untracked = not tracked and not any(x.startswith("git-ls-files-error:") for x in tracked)
        exact_historical_copy = bool(matches)
        exact_retired_oracle_copy = bool(oracle_proof.get("matches"))
        if wholly_untracked and (exact_historical_copy or exact_retired_oracle_copy):
            _remove_legacy_python_tree()
            isolation["action"] = (
                "removed-preexisting-untracked-exact-historical-copy"
                if exact_historical_copy
                else "removed-preexisting-untracked-exact-retired-oracle-copy-with-generated-cache"
            )
            isolation["status"] = (
                "cleaned-preexisting-historical-copy"
                if exact_historical_copy
                else "cleaned-preexisting-retired-oracle-copy"
            )
            expected_final = {
                "legacy_python": {"exists": False, "kind": "missing", "sha256": None, "entries": 0},
                "devtool_config_sha256": before_devtool,
            }
        else:
            isolation["status"] = "FAILED_PREEXISTING_LEGACY_PYTHON_UNPROVEN"
            isolation["reason"] = (
                "preexisting src/dust_tui is not selected by current Devtool configuration, but it was not proven "
                "to be a wholly-untracked exact copy of a prepared historical worktree or the immutable retired oracle; preserving it"
            )

    isolation["expected_final"] = expected_final
    final = _live_tree_baseline()
    isolation["final"] = final
    isolation["final_matches_baseline"] = final == baseline
    isolation["final_matches_expected"] = final == expected_final
    if not isolation["final_matches_expected"] and not isolation["status"].startswith("FAILED_"):
        isolation["status"] = "FAILED_LIVE_TREE_RESTORE_MISMATCH"
        isolation["reason"] = "historical preparation did not leave the current-tree qualification inputs at the proven expected state"
    report["live_tree_isolation"] = isolation
    return isolation["status"] in {
        "clean",
        "cleaned-transient-historical-copy",
        "cleaned-transient-retired-oracle-copy",
        "cleaned-preexisting-historical-copy",
        "cleaned-preexisting-retired-oracle-copy",
    } and isolation["final_matches_expected"]


def discover_gate(entry: dict[str, Any]) -> dict[str, Any]:
    cp = _run("git", "rev-list", "--all", "--reverse")
    out = {"gate": entry["gate"], "node_id": entry["node_id"], "status": "UNRECOVERABLE_HISTORICAL_EVIDENCE", "candidates": []}
    if cp.returncode != 0 or not cp.stdout.strip():
        out["reason"] = "Git history unavailable in this checkout"
        return out
    fingerprints = entry.get("fingerprints", [])
    candidates: list[dict[str, str]] = []
    for commit in cp.stdout.splitlines():
        ok = True
        for fp in fingerprints:
            data = _git_bytes(commit, str(fp["path"]))
            if data is None or hashlib.sha256(data).hexdigest() != fp["sha256"]:
                ok = False
                break
        if not ok:
            continue
        tree = _tree(commit)
        if tree:
            candidates.append({"commit": commit, "tree": tree, "subject": _subject(commit)})
    out["candidates"] = candidates
    if len(candidates) == 1:
        out.update(status="EXACT_TREE_IDENTIFIED_REPLAY_REQUIRED", **candidates[0])
        out["reason"] = "unique commit matches all immutable overlay fingerprints; canonical Devtool replay still required"
    elif len(candidates) > 1:
        out["status"] = "AMBIGUOUS_HISTORICAL_TREE"
        out["reason"] = "multiple commits match all fingerprints; do not guess"
    else:
        out["reason"] = "no commit in local Git history matches all immutable overlay fingerprints"
    return out


def discover_all() -> dict[str, Any]:
    inventory = json.loads(INVENTORY.read_text())
    gates = [discover_gate(x) for x in inventory.get("gates", [])]
    return {
        "schema": "dustui.aud-fix-14-historical-discovery.v2",
        "inventory": str(INVENTORY.relative_to(ROOT)),
        "gates": gates,
        "blockers": [
            {"gate": g["gate"], "status": g["status"], "reason": g.get("reason", "")}
            for g in gates if g["status"] != "REQUALIFIED_EXACT_TREE_PASS"
        ],
        "eligible_for_gate_d": all(g["status"] == "REQUALIFIED_EXACT_TREE_PASS" for g in gates),
    }


def prepare_worktrees(report: dict[str, Any]) -> list[str]:
    prepared: list[str] = []
    WORKTREE_ROOT.mkdir(parents=True, exist_ok=True)
    slug = {"A": "gate-a", "B": "gate-b", "C": "gate-c"}
    for gate in report["gates"]:
        if gate["status"] != "EXACT_TREE_IDENTIFIED_REPLAY_REQUIRED":
            continue
        dst = WORKTREE_ROOT / slug[gate["gate"]]
        commit = gate["commit"]
        if dst.exists():
            cp = _run("git", "rev-parse", "HEAD", cwd=dst)
            if cp.returncode == 0 and cp.stdout.strip() == commit:
                prepared.append(str(dst.relative_to(ROOT)))
                continue
            _run("git", "worktree", "remove", "--force", str(dst))
            if dst.exists():
                shutil.rmtree(dst)
        cp = _run("git", "worktree", "add", "--detach", str(dst), commit)
        if cp.returncode != 0:
            gate["status"] = "WORKTREE_PREPARE_FAILED"
            gate["reason"] = cp.stderr.strip() or cp.stdout.strip()
            continue
        replay_tree = _tree(commit)
        if replay_tree != gate.get("tree"):
            gate["status"] = "WORKTREE_TREE_MISMATCH"
            gate["reason"] = f"prepared worktree tree {replay_tree} != discovered {gate.get('tree')}"
            continue
        gate["worktree"] = str(dst.relative_to(ROOT))
        prepared.append(str(dst.relative_to(ROOT)))
    report["prepared_worktrees"] = prepared
    return prepared


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prepare", action="store_true", help="materialize unique exact-tree matches under .devtool/historical")
    ap.add_argument("--require-recoverable", action="store_true", help="fail while any historical gate lacks requalified exact-tree evidence")
    ap.add_argument("--report", default=str(DEFAULT_REPORT))
    args = ap.parse_args()
    report = discover_all()
    isolation_ok = True
    if args.prepare:
        baseline = _live_tree_baseline()
        prepare_worktrees(report)
        isolation_ok = enforce_live_tree_isolation(report, baseline)
    dst = Path(args.report)
    if not dst.is_absolute():
        dst = ROOT / dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    if not isolation_ok:
        return 1
    return 1 if args.require_recoverable and report["blockers"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
