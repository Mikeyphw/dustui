#!/usr/bin/env python3
from __future__ import annotations

import os
import shlex
import subprocess
import sys
from pathlib import Path


def run_capture(argv: list[str], cwd: Path | None = None) -> str | None:
    try:
        cp = subprocess.run(
            argv,
            cwd=str(cwd) if cwd else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            check=True,
        )
        out = cp.stdout.strip()
        return out or None
    except Exception:
        return None


def find_repo_root() -> Path:
    cwd = Path.cwd().resolve()
    marker = "/.devtool/historical/"
    text = str(cwd)
    if marker in text:
        return Path(text.split(marker, 1)[0]).resolve()

    git_root = run_capture(["git", "rev-parse", "--show-toplevel"], cwd)
    if git_root:
        root = Path(git_root).resolve()
        if root.name == "android" and root.parent.exists():
            return root.parent.resolve()
        return root

    if cwd.name == "android":
        return cwd.parent.resolve()
    return cwd.resolve()


def find_android_runtime_command() -> list[str]:
    env_runtime = os.environ.get("DEVTOOL_ANDROID_BUILD_APK") or os.environ.get("DEVTOOL_ANDROID_RUNTIME")
    candidates: list[Path] = []

    if env_runtime:
        candidates.append(Path(env_runtime).expanduser())

    home = Path.home()
    candidates.extend(
        [
            home / ".local/share/devtool/runtime/android/build-apk",
            home / ".local/share/devtool/runtime/android/build-apk.py",
            home / ".local/share/devtool/runtime/android/build_apk",
            home / ".local/share/devtool/runtime/android/build_apk.py",
            home / ".local/share/devtool/runtime/build-apk",
            home / ".local/share/devtool/runtime/build-apk.py",
        ]
    )

    for c in candidates:
        if c.is_file():
            return [sys.executable, str(c)]

    # Installed editable/dev package fallback. This is still Devtool's Android
    # runner, not raw Gradle. It must never resolve to "." or the repo root.
    try:
        import devtool.android._standalone  # noqa: F401
        return [sys.executable, "-m", "devtool.android._standalone"]
    except Exception as exc:
        searched = "\n".join(f"  - {x}" for x in candidates)
        raise SystemExit(
            "audfix15_android_job_bridge: could not find Devtool Android runtime.\n"
            "Searched:\n"
            f"{searched}\n"
            "Also failed to import devtool.android._standalone.\n"
            f"Import error: {exc}"
        )


def main() -> int:
    tasks = [arg for arg in sys.argv[1:] if arg.strip()]
    if not tasks:
        raise SystemExit("usage: audfix15_android_job_bridge.py :app:task [:app:otherTask ...]")

    root = find_repo_root()
    android_dir = root / "android"
    if not android_dir.is_dir():
        raise SystemExit(f"audfix15_android_job_bridge: android dir not found: {android_dir}")

    runtime_cmd = find_android_runtime_command()

    cmd = [
        *runtime_cmd,
        str(android_dir),
        "--no-chroot",
    ]

    for task in tasks:
        cmd += ["--task", task]

    cmd += [
        "--aapt2-provider", "auto",
        "--android-platform", "36",
        "--build-tools", "auto",
        "--arm-build-tools-provider", "auto",
        "--ndk-version", "auto",
        "--cmake-version", "auto",
        "--gradle-version", "9.7.1",
        "--space-min-free-mb", "4096",
        "--space-target-free-mb", "6144",
        "--managed-gradle-retention", "2",
        "--toolchain-downloads", "auto",
        "--bootstrap-network", "allow",
        "--max-workers", "2",
        "--system-deps", "auto",
        "--daemon-idle-timeout-ms", "900000",
        "--memory-guard-mb", "512",
        "--gradle-jvm-heap-mb", "1152",
        "--gradle-metaspace-mb", "640",
        "--cpu-limit", "2",
        "--constrained",
        "--parallel",
        "--build-cache",
        "--resource-resolution", "android_safe: effective=true environment=native-termux",
        "--resource-resolution", "termux_resource_profile: requested=balanced applied=true",
        "--no-artifact-scan",
    ]

    gradle_tail = [
        "-Dorg.gradle.parallel=true",
        "-Dorg.gradle.vfs.watch=false",
        "-Pkotlin.incremental=true",
        "-Pkotlin.compiler.execution.strategy=in-process",
        "-Pandroid.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE",
        "--no-configuration-cache",
    ]

    init_script = Path.home() / ".local/share/devtool/runtime/gradle/devtool-events.init.gradle"
    if init_script.is_file():
        gradle_tail = ["--init-script", str(init_script), *gradle_tail]

    cmd += ["--", *gradle_tail]

    env = os.environ.copy()
    env.setdefault("JAVA_TOOL_OPTIONS", "-XX:ActiveProcessorCount=2")

    print("audfix15_android_job_bridge:", shlex.join(cmd), flush=True)
    return subprocess.call(cmd, cwd=str(root), env=env)


if __name__ == "__main__":
    raise SystemExit(main())
