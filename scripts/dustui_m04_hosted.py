#!/usr/bin/env python3
"""M04 native hosted Android debug-only Devtool graph/source guard.

`plan` is review-only and never executes Gradle. `run` delegates exact frozen
native job execution to Devtool on GitHub Linux/x64. Not device or release proof.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

TARGET = "dustui_hosted_android"
WORKFLOW = "ci"
DEVTOOL_SHA = "b164463db51e2cffd12f753043b9e55a08cc43ae"
PROFILES = "ubuntu-24.04-x64"
TASKS = [":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"]
OUTPUT = "android/app/build/outputs/apk/debug/app-debug.apk"

class HostedAndroidError(ValueError):
    pass


def checked_graph(graph: dict) -> dict:
    if graph.get("target") != TARGET or graph.get("workflow") != WORKFLOW or graph.get("runtime_sha") != DEVTOOL_SHA:
        raise HostedAndroidError("Devtool target/workflow/runtime drift")
    jobs = graph.get("jobs")
    if not isinstance(jobs, list) or len(jobs) != 1:
        raise HostedAndroidError("M04 requires one native hosted Android node")
    job = jobs[0]
    review = job.get("android_review") or {}
    if (job.get("id") != "node-01" or job.get("job") != "debug_qualified" or
        job.get("provider") != "android-gradle" or job.get("profile_id") != PROFILES or
        job.get("toolchain_version") != "21" or job.get("outputs") != [OUTPUT] or
        job.get("needs") != [] or job.get("handoffs") != []):
        raise HostedAndroidError("M04 native job identity or output drift")
    if (review.get("semantic_request") is not True or review.get("project_dir") != "android" or
        review.get("variant") != "debug" or review.get("java_version") != "21" or
        review.get("wrapper_version") != "9.7.1" or
        review.get("wrapper_jar_sha256") != "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd" or
        review.get("tasks") != TASKS or review.get("operations") != ["test", "lint", "assemble"] or
        review.get("release_qualified") is not False or review.get("device_qualified") is not False or
        review.get("signer_authority") is not False):
        raise HostedAndroidError("hosted Android unit/lint/build or release boundary drift")
    return job


def source_identity(root: Path, hosted: bool) -> str:
    source = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    result = subprocess.run(["git", "-C", str(root), "diff", "--quiet", "HEAD", "--"], check=False)
    if result.returncode:
        raise HostedAndroidError("tracked source differs from committed source")
    if hosted:
        env = os.environ
        if (env.get("GITHUB_ACTIONS") != "true" or env.get("GITHUB_SHA") != source or
            env.get("RUNNER_OS") != "Linux" or env.get("RUNNER_ARCH") != "X64" or
            not env.get("GITHUB_RUN_ID", "").isdigit() or
            not env.get("GITHUB_RUN_ATTEMPT", "").isdigit() or
            not env.get("GITHUB_WORKSPACE") or root != Path(env["GITHUB_WORKSPACE"]).resolve()):
            raise HostedAndroidError("M04 execution requires exact GitHub Linux/x64 source/run/attempt")
    return source


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("plan", "run"))
    p.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    p.add_argument("--inputs")
    p.add_argument("--outputs")
    p.add_argument("--evidence")
    args = p.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        source = source_identity(root, args.mode == "run")
        from devtool.actions_graph_export import compile_graph
        graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                              devtool_sha=DEVTOOL_SHA, host_profiles=PROFILES)
        checked_graph(graph)
        evidence = {"schema": "dustui.devtool.m04.android-debug.v1", "source_sha": source,
                    "devtool_sha": DEVTOOL_SHA, "graph_sha256": graph["graph_sha256"],
                    "status": "SOURCE_REVIEWED_DEBUG_ONLY", "tasks": TASKS, "output": OUTPUT,
                    "release_qualified": False, "device_qualified": False,
                    "authenticated_provenance": False}
        if args.evidence:
            dst = Path(args.evidence)
            if dst.is_symlink() or dst.exists() or not dst.is_absolute():
                raise HostedAndroidError("plan evidence path must be a new absolute regular path")
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
        if args.mode == "plan":
            print(json.dumps(evidence, sort_keys=True))
            return 0
        if not args.inputs or not args.outputs:
            raise HostedAndroidError("hosted run requires Devtool handoff/staging directories")
        from devtool.actions_graph_export import run_node
        result = run_node(root, target=TARGET, workflow=WORKFLOW,
            devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"], job_id="node-01",
            inputs=Path(args.inputs), outputs=Path(args.outputs), host_profiles=PROFILES)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("DUSTUI_M04_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
