#!/usr/bin/env python3
"""Guarded Devtool native release job: Linux GitHub only; exec exact reviewed Gradle tasks."""
from __future__ import annotations
import os
from pathlib import Path
import sys
from dustui_m05_release import root_identity, validate_native_config, TASKS, ReleaseError


def main() -> int:
    try:
        root=Path(__file__).resolve().parents[1]
        if Path.cwd().resolve() != root / "android":
            raise ReleaseError("release Gradle job must execute from android/ target root")
        root_identity(root,hosted=True)
        validate_native_config(root)
        expected=(Path(os.environ["RUNNER_TEMP"]).resolve() / "dustui-m05-private" / "android-signing.properties")
        selected=os.environ.get("DUSTUI_ANDROID_SIGNING_PROPERTIES", "")
        if selected != str(expected) or not expected.is_file() or expected.is_symlink() or expected.stat().st_mode & 0o077:
            raise ReleaseError("reviewed GitHub-only private signing material missing or unsafe")
        launcher=Path.cwd() / "gradlew"
        if not launcher.is_file() or launcher.is_symlink() or not launcher.stat().st_mode & 0o111:
            raise ReleaseError("tracked reviewed Gradle wrapper unavailable")
        os.execv(str(launcher),[str(launcher),"--no-daemon",*TASKS])
    except (OSError,ValueError,KeyError) as exc:
        print("DUSTUI_M05_RELEASE_JOB_REFUSED: "+str(exc)[:220],file=sys.stderr)
        return 2


if __name__=="__main__":
    raise SystemExit(main())
