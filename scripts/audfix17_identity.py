#!/usr/bin/env python3
"""Gate-E qualification identity and reusable-node fingerprints.

The final Gate-E qualification identity is the DustUI source projection. Git
commit/tree IDs are provenance. Expensive execution evidence may be reused
across a new source projection only when the node-specific source/configuration
fingerprint is unchanged; policy/qualification nodes are deliberately rerun.
"""
from __future__ import annotations

import ast
import hashlib
import json
import subprocess
import tomllib
from pathlib import Path
from typing import Any, Iterable

from audfix01_core import REQUIRED_GATE_E_NODES
from audfix16_retirement import included

ROOT = Path(__file__).resolve().parents[1]

# These are the expensive/environment-bearing nodes for which prior passing
# evidence can be composed into a successor qualification when their exact
# product/execution fingerprint is unchanged. Everything else is cheap policy
# truth and is rerun on a changed source projection.
REUSABLE_EXECUTION_NODES: tuple[str, ...] = (
    "canonical_devtool_release",
    "go_full_regression",
    "go_adversarial_failure_windows",
    "go_race_where_supported",
    "android_debug_unit",
    "android_debug_lint",
    "android_debug_assemble",
    "android_release_unit",
    "android_release_lint",
    "android_release_assemble",
    "connected_instrumentation_full",
    "rooted_arm64_runtime",
    "runtime_apk_binding",
)

POLICY_REVALIDATION_NODES: tuple[str, ...] = tuple(
    node for node in REQUIRED_GATE_E_NODES if node not in REUSABLE_EXECUTION_NODES
)

ANDROID_PRODUCT_PATHS: tuple[str, ...] = (
    "android/app/src",
    "android/app/build.gradle.kts",
    "android/app/lint.xml",
    "android/app/proguard-rules.pro",
    "android/build.gradle.kts",
    "android/settings.gradle.kts",
    "android/gradle.properties",
    "android/gradle/libs.versions.toml",
    "android/gradle/wrapper",
    "android/gradlew",
    "scripts/audfix15_android_job_bridge.py",
    "go",
)
GO_PRODUCT_PATHS: tuple[str, ...] = (
    "go",
    "scripts/audfix15_termux_hardlink_compat.py",
)
ROOTED_RUNTIME_PATHS: tuple[str, ...] = (
    "scripts/audfix14_gate_d_probe.py",
    "android/app/src/main",
    "android/app/build.gradle.kts",
    "android/build.gradle.kts",
    "android/settings.gradle.kts",
    "android/gradle/libs.versions.toml",
    "go/internal/runtime",
    "go/internal/privileged",
    "go/internal/rootworker",
)
RUNTIME_BINDING_PATHS: tuple[str, ...] = (
    "scripts/audfix14_gate_d_probe.py",
    "android/app/build.gradle.kts",
    "android/build.gradle.kts",
    "android/settings.gradle.kts",
    "android/gradle/libs.versions.toml",
    "go",
)

NODE_JOB_BINDINGS: dict[str, tuple[str, str] | tuple[str, str, str]] = {
    "go_adversarial_failure_windows": ("job", "dustui_go", "gate_e_adversarial"),
    "android_debug_unit": ("job", "dustui_android", "android_unit"),
    "android_debug_lint": ("job", "dustui_android", "android_lint"),
    "android_debug_assemble": ("job", "dustui_android", "android_assemble"),
    "connected_instrumentation_full": ("job", "dustui_android", "audfix17_instrumentation_full"),
    "rooted_arm64_runtime": ("job", "dustui_android", "audfix17_rooted_arm64_runtime"),
    "runtime_apk_binding": ("job", "dustui_android", "audfix17_runtime_apk_binding"),
}


def _run_git(*args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    cp = subprocess.run(
        ["git", "-c", f"safe.directory={ROOT}", *args], cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        check=False, timeout=60,
    )
    if check and cp.returncode != 0:
        raise RuntimeError(
            f"git {' '.join(args)} failed: {cp.stderr.decode('utf-8', errors='replace').strip()}"
        )
    return cp


def git_text(commit: str, rel: str) -> str:
    cp = _run_git("show", f"{commit}:{rel}", check=False)
    if cp.returncode != 0:
        raise FileNotFoundError(f"{rel} not present at {commit}")
    return cp.stdout.decode("utf-8", errors="surrogateescape")


def git_json(commit: str, rel: str) -> dict[str, Any]:
    value = json.loads(git_text(commit, rel))
    if not isinstance(value, dict):
        raise ValueError(f"{rel} at {commit} is not a JSON object")
    return value


def git_config(commit: str) -> dict[str, Any]:
    return tomllib.loads(git_text(commit, ".devtool.toml"))


def commit_exists(commit: str) -> bool:
    if not commit:
        return False
    return _run_git("cat-file", "-e", f"{commit}^{{commit}}", check=False).returncode == 0


def _ls_tree(commit: str, rel: str) -> list[tuple[str, str]]:
    rel = rel.strip("/") or "."
    args = ["ls-tree", "-r", "-z", "--full-tree", commit]
    if rel != ".":
        args += ["--", rel]
    cp = _run_git(*args, check=False)
    if cp.returncode != 0:
        return []
    out: list[tuple[str, str]] = []
    for raw in cp.stdout.split(b"\0"):
        if not raw:
            continue
        try:
            meta, path_b = raw.split(b"\t", 1)
            _mode, kind, oid = meta.decode("ascii").split(" ", 2)
            path = path_b.decode("utf-8", errors="surrogateescape")
        except Exception:
            continue
        if kind != "blob" or not included(path):
            continue
        out.append((path, oid))
    return out


def _paths_digest(commit: str, paths: Iterable[str]) -> tuple[str, list[str]]:
    entries: dict[str, str] = {}
    missing: list[str] = []
    for rel in paths:
        rows = _ls_tree(commit, rel)
        if not rows:
            missing.append(rel)
        for path, oid in rows:
            entries[path] = oid
    h = hashlib.sha256()
    for path in sorted(entries):
        h.update(f"F\0{path}\0{entries[path]}\n".encode("utf-8", errors="surrogateescape"))
    for rel in sorted(missing):
        h.update(f"MISSING\0{rel}\n".encode())
    return h.hexdigest(), sorted(entries)


def _job_semantics(cfg: dict[str, Any], target: str, job: str) -> dict[str, Any]:
    target_obj = cfg.get("targets", {}).get(target, {})
    job_obj = target_obj.get("jobs", {}).get(job, {})
    if not job_obj:
        raise KeyError(f"missing Devtool job {target}.{job}")
    return {
        "target": target,
        "target_root": target_obj.get("root"),
        "job": job,
        "runner": job_obj.get("runner"),
        "root": job_obj.get("root"),
        "command": job_obj.get("command"),
        "inputs": job_obj.get("inputs"),
    }


def _workflow_jobs(cfg: dict[str, Any], target: str, workflow: str) -> list[str]:
    target_obj = cfg.get("targets", {}).get(target, {})
    rows = target_obj.get("workflows", {}).get(workflow, [])
    out: list[str] = []
    for row in rows:
        ref = row.get("ref") if isinstance(row, dict) else row
        if isinstance(ref, str) and ref.startswith("job:"):
            out.append(ref[4:])
    return out


def _function_ast_sha256(commit: str, rel: str, function: str) -> str:
    tree = ast.parse(git_text(commit, rel), filename=rel)
    for item in tree.body:
        if isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)) and item.name == function:
            payload = ast.dump(item, annotate_fields=True, include_attributes=False).encode()
            return hashlib.sha256(payload).hexdigest()
    raise KeyError(f"function {function} not found in {rel} at {commit}")


def _semantic_payload(commit: str, node: str) -> dict[str, Any]:
    cfg = git_config(commit)
    if node == "canonical_devtool_release" or node in {
        "android_release_unit", "android_release_lint", "android_release_assemble"
    }:
        release = cfg.get("targets", {}).get("dustui_android", {}).get("android", {}).get("release", {})
        return {"kind": "canonical-android-release", "release": release}
    if node == "go_full_regression":
        jobs = _workflow_jobs(cfg, "dustui_go", "gate_e_core")
        return {
            "kind": "workflow",
            "target": "dustui_go",
            "workflow": "gate_e_core",
            "jobs": [_job_semantics(cfg, "dustui_go", j) for j in jobs],
        }
    if node == "go_race_where_supported":
        return {
            "kind": "gate-e-go-race",
            "function_ast_sha256": _function_ast_sha256(commit, "scripts/audfix17_gate_e.py", "go_race"),
        }
    binding = NODE_JOB_BINDINGS.get(node)
    if binding and binding[0] == "job":
        return {"kind": "job", "job": _job_semantics(cfg, binding[1], binding[2])}
    raise KeyError(f"no reusable-node semantic fingerprint specification for {node}")


def _source_paths(node: str) -> tuple[str, ...]:
    if node == "canonical_devtool_release" or node in {
        "android_debug_unit", "android_debug_lint", "android_debug_assemble",
        "android_release_unit", "android_release_lint", "android_release_assemble",
        "connected_instrumentation_full",
    }:
        return ANDROID_PRODUCT_PATHS
    if node in {"go_full_regression", "go_adversarial_failure_windows", "go_race_where_supported"}:
        return GO_PRODUCT_PATHS
    if node == "rooted_arm64_runtime":
        return ROOTED_RUNTIME_PATHS
    if node == "runtime_apk_binding":
        return RUNTIME_BINDING_PATHS
    raise KeyError(f"no reusable-node source fingerprint specification for {node}")


def node_fingerprint(commit: str, node: str) -> dict[str, Any]:
    if node not in REUSABLE_EXECUTION_NODES:
        raise KeyError(f"{node} is policy-revalidated and has no reusable execution fingerprint")
    if not commit_exists(commit):
        raise RuntimeError(f"Git commit unavailable for Gate-E fingerprint: {commit}")
    path_digest, files = _paths_digest(commit, _source_paths(node))
    semantics = _semantic_payload(commit, node)
    payload = {
        "schema": "dustui.gate-e.node-input-fingerprint.v2",
        "node": node,
        "source_files_sha256": path_digest,
        "execution_semantics": semantics,
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return {
        **payload,
        "fingerprint_sha256": hashlib.sha256(canonical).hexdigest(),
        "tracked_file_count": len(files),
    }


def reusable_fingerprints(commit: str) -> dict[str, dict[str, Any]]:
    return {node: node_fingerprint(commit, node) for node in REUSABLE_EXECUTION_NODES}


def compare_reusable_fingerprints(validated_commit: str, current_commit: str) -> dict[str, Any]:
    old = reusable_fingerprints(validated_commit)
    new = reusable_fingerprints(current_commit)
    rows: dict[str, Any] = {}
    changed: list[str] = []
    reusable: list[str] = []
    for node in REUSABLE_EXECUTION_NODES:
        same = old[node]["fingerprint_sha256"] == new[node]["fingerprint_sha256"]
        rows[node] = {
            "reusable": same,
            "validated_fingerprint_sha256": old[node]["fingerprint_sha256"],
            "current_fingerprint_sha256": new[node]["fingerprint_sha256"],
            "tracked_file_count": new[node]["tracked_file_count"],
        }
        (reusable if same else changed).append(node)
    return {
        "schema": "dustui.gate-e.reuse-plan.v2",
        "validated_git_head": validated_commit,
        "current_git_head": current_commit,
        "reusable_execution_nodes": reusable,
        "changed_execution_nodes": changed,
        "policy_revalidation_nodes": list(POLICY_REVALIDATION_NODES),
        "nodes": rows,
    }


def validate_node_partition() -> None:
    required = tuple(REQUIRED_GATE_E_NODES)
    heavy = set(REUSABLE_EXECUTION_NODES)
    policy = set(POLICY_REVALIDATION_NODES)
    if heavy & policy:
        raise AssertionError("Gate-E reuse node partition overlaps")
    if heavy | policy != set(required):
        raise AssertionError("Gate-E reuse node partition does not cover required topology")


validate_node_partition()

if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("validated_commit")
    ap.add_argument("current_commit", nargs="?", default="HEAD")
    args = ap.parse_args()
    print(json.dumps(compare_reusable_fingerprints(args.validated_commit, args.current_commit), indent=2, sort_keys=True))
