#!/usr/bin/env python3
"""DustUI M02 current Devtool config contract. Stdlib only; no Gradle, device, network or writes."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_TARGETS = {"dustui_go", "dustui_hosted_go", "dustui_hosted_r36"}
LOCAL_COMMANDS = {"ci-preflight", "ci-local", "go-fast", "go-test", "go-vet", "go-build"}
HOSTED_TASKS = [":app:lintDebug", ":app:assembleDebug"]
HOSTED_OUTPUT = "android/app/build/outputs/apk/debug/app-debug.apk"
HOSTED_WRAPPER_JAR = "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"


class ContractError(ValueError):
    pass


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ContractError(reason)


def check_document(raw: dict, root: Path = ROOT) -> dict:
    require(isinstance(raw, dict), "config must be TOML table")
    targets = raw.get("targets", {})
    require(isinstance(targets, dict) and set(targets) == EXPECTED_TARGETS,
            "target inventory must be current Go, hosted Go, and R36 debug only")
    workspace = raw.get("workspace", {})
    require(workspace.get("default_target") == "dustui_go", "default must not be Android")
    sched = workspace.get("scheduler", {})
    require(sched.get("parallel") is True and 1 <= sched.get("max_parallel_nodes", 0) <= 3,
            "bounded scheduler required")
    require(sched.get("max_gradle_nodes") == 1, "Gradle concurrency must stay bounded")
    wrapper = raw.get("wrapper", {})
    require(wrapper.get("schema") == 2, "wrapper v2 required")
    require(wrapper.get("default_command") == "ci-preflight", "unsafe default command")
    commands = wrapper.get("commands", {})
    require(isinstance(commands, dict) and set(commands) == LOCAL_COMMANDS,
            "wrapper surface must be six reviewed local commands")
    assert_commands = set()
    go_local = targets["dustui_go"]
    require(go_local.get("root") == "go" and go_local.get("type") == "go", "local Go root must be go/")
    require(go_local.get("execution_environment") == "auto", "local Go must run on both Termux and desktop")
    local_jobs = go_local.get("jobs", {})
    require(set(local_jobs) == {"source", "test", "vet", "build"}, "local Go job inventory changed")
    require(local_jobs["source"].get("root") == "..", "source-only preflight must run at repo root")
    require(local_jobs["source"].get("command") == ["python3", "scripts/devtool_actions_contract.py"],
            "source validation must use exact Python script")
    for key in ("test", "vet", "build"):
        job = local_jobs[key]
        require(job.get("runner") == "command" and job.get("command") == ["go", key if key != "build" else "build", "./..."],
                f"local {key} must be direct Go argv")
        require(job.get("metadata", {}).get("resource_class") == "cpu-heavy", "Go jobs need bounded resource class")
    for name, command in commands.items():
        require(command.get("target") == "dustui_go", f"wrapper {name} may only use local Go target")
        workflow = command.get("workflow")
        require(workflow in go_local.get("workflows", {}), f"wrapper {name} references unknown workflow")
        require(not any(k in command for k in ("devtool", "actions", "allow_passthrough", "all_targets", "group")),
                f"wrapper {name} contains unreviewed execution delegation")
        assert_commands.add(workflow)
    require(go_local["workflows"]["ci-preflight"] == ["job:source"], "source preflight graph changed")
    dag = go_local["workflows"]["ci-local"]
    require(isinstance(dag, list) and len(dag) == 4, "local CI DAG must have four nodes")
    seen = set()
    for step in dag:
        require(isinstance(step, dict) and set(step) == {"id", "ref", "depends_on"}, "unexpected CI DAG syntax")
        require(step["ref"] == "job:" + step["id"] and set(step["depends_on"]) <= seen,
                "CI DAG references must be local and topologically ordered")
        require(step["id"] not in seen, "duplicate CI node")
        seen.add(step["id"])
    require(seen == {"source", "test", "vet", "build"}, "CI DAG incomplete")
    hosted = targets["dustui_hosted_go"]
    require(hosted.get("runner") == "go" and hosted.get("root") == "." and hosted.get("execution_environment") == "auto",
            "hosted Go provider target changed")
    require(set(hosted.get("jobs", {})) == {"test", "vet", "build"}, "hosted Go inventory changed")
    for k, job in hosted["jobs"].items():
        require(job.get("runner") == "go" and job.get("command") == ["go", "-C", "go", k, "./..."],
                f"hosted Go job {k} is not an approved direct provider command")
        require(job.get("metadata", {}).get("ci_toolchain") == "go" and job.get("outputs") == [],
                f"hosted Go job {k} must not claim output authority")
        require(job.get("inputs") == ["go/go.mod", "go/go.sum"], f"hosted Go {k} must pin manifests")
    require(len(hosted.get("workflows", {}).get("ci", [])) == 3, "hosted Go native DAG incomplete")
    android = targets["dustui_hosted_r36"]
    require(android.get("runner") == "android" and android.get("root") == "android",
            "R36 target identity drift")
    require(set(android.get("jobs", {})) == {"debug_apk"}, "R36 must be debug-only")
    job = android["jobs"]["debug_apk"]
    require(job.get("runner") == "android" and job.get("gradle_tasks") == HOSTED_TASKS,
            "R36 exact debug task identity drift")
    require(job.get("outputs") == [HOSTED_OUTPUT], "R36 APK output drift")
    meta = job.get("metadata", {})
    require(meta.get("ci_toolchain") == "android-gradle" and meta.get("ci_android_project_dir") == "android"
            and meta.get("ci_android_java_version") == "21" and meta.get("ci_android_debug_variant") == "debug"
            and meta.get("ci_android_wrapper_jar_sha256") == HOSTED_WRAPPER_JAR
            and meta.get("ci_android_expected_output") == HOSTED_OUTPUT,
            "R36 reviewed toolchain or output pin drift")
    require(meta.get("ci_android_sdk_packages") == ["platforms;android-36", "build-tools;36.0.0", "platform-tools"],
            "R36 SDK pins drift")
    require(android.get("workflows", {}).get("ci") == ["job:debug_apk"], "R36 workflow contract drift")
    require((root / ".github/workflows/devtool-dustui-r36-hosted-debug.yml").is_file(),
            "R36 workflow must remain until M04 replacement")
    require((root / "scripts/dustui_r36_hosted.py").is_file(), "R36 runner bridge missing")
    return {"status": "PASS", "stage": "M02", "target_count": len(targets),
            "targets": sorted(targets), "local_wrapper_commands": sorted(commands),
            "hosted_go": "DECLARED_NOT_YET_QUALIFIED", "android_r36": "RETAINED_DEBUG_ONLY"}


def main() -> int:
    try:
        raw = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
        report = check_document(raw)
        print(json.dumps(report, sort_keys=True))
        return 0
    except (ValueError, KeyError, OSError, TypeError, tomllib.TOMLDecodeError) as exc:
        print("DUSTUI_M02_CONTRACT_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
