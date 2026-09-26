#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"],
        text=True,
        stderr=subprocess.DEVNULL,
    ).strip()
    return Path(out)


def parse_tasks(argv: list[str]) -> list[str]:
    tasks: list[str] = []
    skip_next = False

    for arg in argv:
        if skip_next:
            skip_next = False
            continue

        if arg == "--warning-mode":
            skip_next = True
            continue

        if arg.startswith("-D") or arg.startswith("-P"):
            continue

        if arg.startswith("--"):
            continue

        if arg.startswith("-"):
            continue

        tasks.append(arg)

    return tasks


def main(argv: list[str]) -> int:
    root = repo_root()
    tasks = parse_tasks(argv)

    if not tasks:
        raise SystemExit("No Gradle task supplied; use devtool --yes validate --task <task>")

    cmd = ["devtool", "--yes", "validate"]
    for task in tasks:
        cmd.extend(["--task", task])

    print("[devtool-gradle-bridge]", " ".join(cmd), flush=True)
    return subprocess.run(cmd, cwd=root).returncode


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
