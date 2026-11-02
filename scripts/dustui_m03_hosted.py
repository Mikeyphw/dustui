#!/usr/bin/env python3
"""M03: source-pinned native Devtool Go DAG on GitHub. No alternate CI runner.

`plan` is read-only and can run locally with the pinned Devtool installed.
`run` requires the exact GitHub SHA/run/attempt, selected linux-x64 runner,
and delegates to `devtool.actions_graph_export.run_node` for typed evidence.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

TARGET = "dustui_hosted_go"
WORKFLOW = "ci"
DEVTOOL_SHA = "7cb00a850dae07f7fe9ca7e17eb264cb132d8183"
PROFILES = "ubuntu-24.04-x64"
JOB_ORDER = ("test", "vet", "race", "build")
COMMANDS = {
    "test": ["go", "-C", "go", "test", "./..."],
    "vet": ["go", "-C", "go", "vet", "./..."],
    "race": ["go", "-C", "go", "test", "-race", "./..."],
    "build": ["go", "-C", "go", "build", "./..."],
}


class HostedGoError(ValueError):
    pass


def assert_graph(graph: dict) -> dict[str, str]:
    """Guard every expected native node and predecessor; fail closed on drift."""
    if graph.get("target") != TARGET or graph.get("workflow") != WORKFLOW or graph.get("runtime_sha") != DEVTOOL_SHA:
        raise HostedGoError("unexpected target/workflow/Devtool runtime pin")
    jobs = graph.get("jobs")
    if not isinstance(jobs, list) or len(jobs) != 4:
        raise HostedGoError("expected exactly four hosted Go native DAG nodes")
    names = {}
    for i, job in enumerate(jobs, 1):
        name = JOB_ORDER[i - 1]
        node = f"node-{i:02d}"
        if job.get("id") != node or job.get("job") != name or job.get("provider") != "go":
            raise HostedGoError(f"native Go node identity drift: {name}")
        if job.get("profile_id") != PROFILES or job.get("go_command") != COMMANDS[name] or job.get("outputs") != []:
            raise HostedGoError(f"hosted Go provider/argv/output drift: {name}")
        if job.get("go_manifest") != "go/go.mod":
            raise HostedGoError("Go toolchain version must come from tracked go/go.mod")
        if bool(job.get("race")) != (name == "race"):
            raise HostedGoError(f"race instrumentation drift: {name}")
        expected_env = {"GOOS": "linux", "GOARCH": "amd64", "CGO_ENABLED": "1"} if name == "race" else {}
        if job.get("go_env") != expected_env:
            raise HostedGoError(f"Go hosted environment drift: {name}")
        expected_needs = ["node-01", "node-02", "node-03"] if name == "build" else []
        if job.get("needs") != expected_needs:
            raise HostedGoError(f"native DAG dependencies drift: {name}")
        names[name] = node
    return names


def checkout_identity(root: Path, *, require_github: bool) -> str:
    try:
        source = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        dirty = subprocess.run(["git", "-C", str(root), "diff", "--quiet", "HEAD", "--"], check=False)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise HostedGoError(f"Git repository source unavailable: {exc}") from exc
    if dirty.returncode != 0:
        raise HostedGoError("tracked source differs from committed source")
    if require_github:
        env = os.environ
        if (env.get("GITHUB_ACTIONS") != "true" or source != env.get("GITHUB_SHA") or
            env.get("RUNNER_OS") != "Linux" or env.get("RUNNER_ARCH") != "X64" or
            not env.get("GITHUB_RUN_ID", "").isdigit() or not env.get("GITHUB_RUN_ATTEMPT", "").isdigit() or
            not env.get("GITHUB_WORKSPACE") or root != Path(env["GITHUB_WORKSPACE"]).resolve()):
            raise HostedGoError("M03 requires exact GitHub Linux/x64 source/run/attempt context")
    return source


def checked_graph(root: Path) -> dict:
    from devtool.actions_graph_export import compile_graph
    graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, host_profiles=PROFILES)
    assert_graph(graph)
    return graph


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "run"))
    parser.add_argument("--node", choices=JOB_ORDER, required=True)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--inputs")
    parser.add_argument("--outputs")
    parser.add_argument("--evidence")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        source = checkout_identity(root, require_github=args.mode == "run")
        graph = checked_graph(root)
        node_id = f"node-{JOB_ORDER.index(args.node) + 1:02d}"
        plan = {"schema": "dustui.devtool.m03.hosted-go.v1", "status": "SOURCE_REVIEWED_NOT_RELEASE",
                "target": TARGET, "workflow": WORKFLOW, "node": args.node,
                "node_id": node_id, "graph_sha256": graph["graph_sha256"],
                "devtool_sha": DEVTOOL_SHA, "source_sha": source,
                "device_qualified": False, "release_qualified": False,
                "signed_artifact": False, "remote_authenticated_attestation": False}
        if args.evidence:
            out = Path(args.evidence)
            if out.exists() or out.is_symlink():
                raise HostedGoError("evidence output may not overwrite an existing path")
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(plan, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        if args.mode == "plan":
            print(json.dumps(plan, sort_keys=True))
            return 0
        if not args.inputs or not args.outputs:
            raise HostedGoError("run requires explicit Devtool-owned staging paths")
        from devtool.actions_graph_export import run_node
        result = run_node(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"],
                          job_id=node_id, host_profiles=PROFILES,
                          inputs=Path(args.inputs), outputs=Path(args.outputs))
        if result.get("status") != "LOCAL_JOB_SUCCEEDED" or result.get("job_id") != node_id or not result.get("typed_node_evidence"):
            raise HostedGoError("native runner returned insufficient typed node evidence")
        print(json.dumps({"status": result["status"], "node": args.node,
                          "job_id": node_id, "graph_sha256": result["graph_sha256"],
                          "typed_evidence": True, "release_qualified": False}, sort_keys=True))
        return 0
    except (HostedGoError, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("DUSTUI_M03_HOSTED_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
