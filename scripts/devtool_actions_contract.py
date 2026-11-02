#!/usr/bin/env python3
"""DustUI M02 current Devtool config contract. Stdlib only; no Gradle, device, network or writes."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_TARGETS = {"dustui_go", "dustui_hosted_go", "dustui_hosted_r36", "dustui_hosted_android", "dustui_hosted_release"}
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
    require(set(hosted.get("jobs", {})) == {"test", "vet", "race", "build"}, "hosted Go inventory changed")
    for k, job in hosted["jobs"].items():
        expected_command = ["go", "-C", "go", "test", "-race", "./..."] if k == "race" else ["go", "-C", "go", k, "./..."]
        require(job.get("runner") == "go" and job.get("command") == expected_command,
                f"hosted Go job {k} is not an approved direct provider command")
        require(job.get("metadata", {}).get("ci_toolchain") == "go" and job.get("outputs") == [],
                f"hosted Go job {k} must not claim output authority")
        require(job.get("inputs") == ["go/go.mod", "go/go.sum"], f"hosted Go {k} must pin manifests")
        require(job.get("metadata", {}).get("resource_class") == "cpu-heavy", "hosted Go must reserve CPU resources")
        if k == "race":
            require(job.get("env") == {"GOOS": "linux", "GOARCH": "amd64", "CGO_ENABLED": "1"},
                    "race must be restricted to hosted linux/amd64 with CGO")
        else:
            require("env" not in job, "non-race hosted job must not override toolchain env")
    native = hosted.get("workflows", {}).get("ci", [])
    require(len(native) == 4, "hosted Go native DAG must contain test, vet, race, build")
    require([n.get("id") for n in native] == ["test", "vet", "race", "build"], "hosted DAG ordering changed")
    require([n.get("ref") for n in native] == ["job:test", "job:vet", "job:race", "job:build"],
            "hosted Go DAG must use native target-owned jobs")
    require(all(n.get("depends_on") == [] for n in native[:3]) and native[3].get("depends_on") == ["test", "vet", "race"],
            "hosted Go requires three independent gates before build")
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
    new_android = targets["dustui_hosted_android"]
    require(new_android.get("root") == "android" and new_android.get("runner") == "android" and
            new_android.get("execution_environment") == "auto", "M04 must remain hosted Android only")
    require(set(new_android.get("jobs", {})) == {"debug_qualified"}, "M04 Android job inventory changed")
    qualified = new_android["jobs"]["debug_qualified"]
    require(qualified.get("runner") == "android" and qualified.get("gradle_tasks") ==
            [":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"],
            "M04 requires reviewed debug unit, lint, assemble tasks without release/device tasks")
    require(qualified.get("outputs") == [HOSTED_OUTPUT], "M04 debug APK output drift")
    qm = qualified.get("metadata", {})
    require(qm.get("resource_class") == "gradle" and qm.get("ci_toolchain") == "android-gradle" and
            qm.get("ci_android_project_dir") == "android" and qm.get("ci_android_java_version") == "21" and
            qm.get("ci_android_debug_variant") == "debug" and
            qm.get("ci_android_wrapper_jar_sha256") == HOSTED_WRAPPER_JAR and
            qm.get("ci_android_expected_output") == HOSTED_OUTPUT and
            qm.get("ci_android_sdk_packages") == ["platforms;android-36", "build-tools;36.0.0", "platform-tools"],
            "M04 hosted Android provider identity drift")
    require(new_android.get("workflows", {}).get("ci") == ["job:debug_qualified"],
            "M04 must use one reviewed native Devtool node")
    require((root / ".github/workflows/devtool-dustui-m04-hosted-debug.yml").is_file(),
            "M04 GitHub hosted workflow missing")
    require((root / "scripts/dustui_m04_hosted.py").is_file(), "M04 reviewed native executor missing")
    require((root / "scripts/dustui_m04_verify_apk.py").is_file(), "M04 package verifier missing")
    require((root / ".github/workflows/devtool-dustui-r36-hosted-debug.yml").is_file(),
            "R36 workflow must remain until M04 replacement")
    require((root / "scripts/dustui_r36_hosted.py").is_file(), "R36 runner bridge missing")
    workflow_path = root / ".github/workflows/devtool-dustui.yml"
    require(workflow_path.is_file(), "hosted Go GitHub workflow missing")
    wf = workflow_path.read_text(encoding="utf-8")
    require("scripts/dustui_m03_hosted.py run" in wf and "DEVTOOL_SOURCE_SHA:" in wf,
            "hosted CI must execute source-pinned Devtool native nodes")
    require("gradlew" not in wf and "./gradle" not in wf,
            "no direct Gradle in Go CI; R36 owns hosted Android")
    require("go test ./..." not in wf and "go build ./..." not in wf,
            "hosted Go CI must run through Devtool rather than direct shell")
    require("permissions:\n  contents: read" in wf, "GitHub token must be read-only")
    # M05 release is deliberately separate from the debug-only native hosted provider.
    release = targets["dustui_hosted_release"]
    rjob = release.get("jobs", {}).get("release_build", {})
    require(release.get("runner") == "command" and release.get("root") == "android"
            and release.get("execution_environment") == "auto", "M05 release is hosted command target")
    require(set(release.get("jobs", {})) == {"release_build"} and
            release.get("workflows", {}).get("ci") == ["job:release_build"],
            "M05 release job inventory changed")
    require(rjob.get("runner") == "command" and rjob.get("root") == "." and
            rjob.get("command") == ["python3", "../scripts/dustui_m05_release_job.py"] and
            rjob.get("outputs") == ["android/app/build/outputs/apk/release/app-release.apk"] and
            rjob.get("metadata", {}).get("resource_class") == "gradle" and
            rjob.get("metadata", {}).get("global_reservations") == ["android-gradle"],
            "M05 release must use guarded native Devtool job, never local Gradle")
    require(all(c.get("target") != "dustui_hosted_release" for c in commands.values()),
            "M05 release must not be exposed through local wrapper")
    release_wf = root / ".github/workflows/devtool-dustui-m05-hosted-release.yml"
    require(release_wf.is_file() and (root / "scripts/dustui_m05_release.py").is_file() and
            (root / "scripts/dustui_m05_release_job.py").is_file() and
            (root / "scripts/dustui_m05_device.py").is_file(),
            "M05 release/device surfaces missing")
    wf_text = release_wf.read_text()
    require("workflow_dispatch:" in wf_text and "pull_request:" not in wf_text and
            "  push:" not in wf_text and "dustui-release" in wf_text and
            "scripts/dustui_m05_release.py verify" in wf_text and
            "--target dustui_hosted_release invoke ci" in wf_text and
            "DUSTUI_KEYSTORE_BASE64" in wf_text and "DUSTUI_EXPECTED_SIGNER_SHA256" in wf_text,
            "M05 release secrets/dispatch/invoke/verification contract drift")
    return {"status": "PASS", "stage": "M05", "target_count": len(targets), "hosted_nodes": 4,
            "targets": sorted(targets), "local_wrapper_commands": sorted(commands),
            "hosted_go": "NATIVE_DAG_CONFIGURED_REMOTE_UNVERIFIED", "android_r36": "RETAINED_DEBUG_ONLY"}


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
