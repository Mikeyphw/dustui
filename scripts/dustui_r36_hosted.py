#!/usr/bin/env python3
"""DustUI R36 consumer admission: native Devtool graph, never local Gradle.

`plan` is read-only. `run` delegates to Devtool's exact native RM-06 run-node
with the same reviewed graph. No project-specific Gradle execution exists here.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

TARGET = "dustui_hosted_r36"
WORKFLOW = "ci"
DEVTOOL_SHA = "246f168ebdef6d9b25c194268c810c877d099fa3"
TASKS = [":app:lintDebug", ":app:assembleDebug"]
OUTPUT = "android/app/build/outputs/apk/debug/app-debug.apk"
PROFILES = "ubuntu-24.04-x64"


def _git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def checked_graph(root: Path) -> dict:
    from devtool.actions_graph_export import compile_graph
    graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, host_profiles=PROFILES)
    nodes = graph["jobs"]
    if len(nodes) != 1 or nodes[0]["id"] != "node-01":
        raise ValueError("DustUI R36 requires exactly one reviewed native DAG node")
    node = nodes[0]
    review = node.get("android_review") or {}
    if (node["provider"] != "android-gradle"
            or review.get("semantic_request") is not True
            or review.get("project_dir") != "android"
            or review.get("variant") != "debug"
            or review.get("java_version") != "21"
            or review.get("wrapper_version") != "9.7.1"
            or review.get("tasks") != TASKS
            or node["outputs"] != [OUTPUT]
            or review.get("signer_authority") is not False
            or review.get("release_qualified") is not False
            or review.get("device_qualified") is not False):
        raise ValueError("DustUI R36 debug-only provider or output contract changed")
    return graph


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "run"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--inputs")
    parser.add_argument("--outputs")
    parser.add_argument("--evidence")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        graph = checked_graph(root)
        source = _git(root, "rev-parse", "HEAD")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            if (source != os.environ.get("GITHUB_SHA")
                    or os.environ.get("RUNNER_OS") != "Linux"
                    or os.environ.get("RUNNER_ARCH") != "X64"):
                raise ValueError("DustUI R36 exact GitHub Linux source identity missing")
        plan = {"schema": "dustui.r36.hosted-plan.v1", "status": "REVIEWED_DEBUG_ONLY",
                "source_sha": source, "devtool_sha": DEVTOOL_SHA,
                "graph_sha256": graph["graph_sha256"], "tasks": TASKS,
                "output": OUTPUT, "release_qualified": False,
                "device_qualified": False, "authenticated_artifact": False}
        if args.evidence:
            out = Path(args.evidence)
            if out.is_symlink() or out.exists():
                raise ValueError("R36 evidence destination already exists")
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(plan, indent=2, sort_keys=True) + "\n")
        if args.mode == "plan":
            print(json.dumps(plan, sort_keys=True))
            return 0
        if (os.environ.get("GITHUB_ACTIONS") != "true"
                or not args.inputs or not args.outputs):
            raise ValueError("R36 run is hosted only; plan is the local read-only mode")
        from devtool.actions_graph_export import run_node
        result = run_node(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"],
                          job_id="node-01", inputs=Path(args.inputs),
                          outputs=Path(args.outputs), host_profiles=PROFILES)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("DUSTUI_R36_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
