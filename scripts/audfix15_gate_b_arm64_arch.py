#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


def unique_paths(paths: list[str | None]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for item in paths:
        if not item:
            continue
        p = str(Path(item).expanduser())
        if p in seen:
            continue
        seen.add(p)
        out.append(p)
    return out


def adb_candidates() -> list[str]:
    android_home = os.environ.get("ANDROID_HOME")
    android_sdk_root = os.environ.get("ANDROID_SDK_ROOT")
    return unique_paths([
        os.environ.get("DEVTOOL_ADB"),
        os.environ.get("ADB"),
        "/data/data/com.termux/files/usr/bin/adb",
        shutil.which("adb"),
        str(Path(android_home) / "platform-tools" / "adb") if android_home else None,
        str(Path(android_sdk_root) / "platform-tools" / "adb") if android_sdk_root else None,
    ])


def run(args: list[str], timeout: int = 20) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)


def pick_adb() -> tuple[str, list[dict[str, object]]]:
    attempts: list[dict[str, object]] = []
    for adb in adb_candidates():
        p = Path(adb)
        if not p.exists():
            attempts.append({"adb": adb, "ok": False, "reason": "missing"})
            continue
        try:
            result = run([adb, "version"], timeout=10)
        except Exception as exc:
            attempts.append({"adb": adb, "ok": False, "reason": type(exc).__name__, "detail": str(exc)})
            continue

        combined = (result.stdout + "\n" + result.stderr).strip()
        ok = result.returncode == 0 and "Android Debug Bridge" in combined
        attempts.append({
            "adb": adb,
            "ok": ok,
            "exit": result.returncode,
            "firstLine": combined.splitlines()[0] if combined else "",
        })
        if ok:
            return adb, attempts

    print(json.dumps({
        "ok": False,
        "error": "NO_WORKING_ADB",
        "message": "No working adb found. On Termux, install/use the Termux android-tools adb, not the SDK platform-tools adb.",
        "attempts": attempts,
    }, indent=2))
    raise SystemExit(2)


def connected_devices(adb: str) -> list[dict[str, str]]:
    result = run([adb, "devices", "-l"], timeout=20)
    if result.returncode != 0:
        print(json.dumps({
            "ok": False,
            "error": "ADB_DEVICES_FAILED",
            "adb": adb,
            "stdout": result.stdout,
            "stderr": result.stderr,
        }, indent=2))
        raise SystemExit(result.returncode or 2)

    devices: list[dict[str, str]] = []
    for line in result.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append({"serial": parts[0], "raw": line})
    return devices


def main() -> int:
    adb, attempts = pick_adb()
    requested_serial = os.environ.get("ANDROID_SERIAL", "").strip()
    devices = connected_devices(adb)

    if requested_serial:
        serial = requested_serial
    elif len(devices) == 1:
        serial = devices[0]["serial"]
    else:
        print(json.dumps({
            "ok": False,
            "error": "ANDROID_SERIAL_REQUIRED",
            "message": "Gate B arm64 replay needs exactly one adb device or ANDROID_SERIAL.",
            "adb": adb,
            "devices": devices,
            "attempts": attempts,
        }, indent=2))
        return 2

    result = run([adb, "-s", serial, "shell", "getprop", "ro.product.cpu.abi"], timeout=20)
    abi = result.stdout.strip().splitlines()[0].strip() if result.stdout.strip() else ""

    ok = result.returncode == 0 and abi in {"arm64-v8a", "arm64", "aarch64"}
    print(json.dumps({
        "ok": ok,
        "schema": "dustui.gate-b.arm64-arch.v1",
        "adb": adb,
        "serial": serial,
        "abi": abi,
        "accepted": ["arm64-v8a", "arm64", "aarch64"],
        "attempts": attempts,
        "stdout": result.stdout,
        "stderr": result.stderr,
    }, indent=2))

    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
