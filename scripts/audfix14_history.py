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
    if args.prepare:
        prepare_worktrees(report)
    dst = Path(args.report)
    if not dst.is_absolute():
        dst = ROOT / dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 1 if args.require_recoverable and report["blockers"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
