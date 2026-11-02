#!/usr/bin/env python3
"""M05 exact-APK ADB importer. Never builds with Gradle or runs destructive cleanup.

'plan' verifies a downloaded GitHub APK against its M04/M05 inspection JSON;
'run' requires explicit opt-in to install/launch on a selected ARM64 device.
Missing Binder, SQLite, route, root and cleanup behavior remains NOT_RUN.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from dustui_m04_verify_apk import inspect_zip, APKError


class DeviceError(ValueError):
    pass


def receipt_check(apk: Path, receipt: Path) -> dict:
    if not apk.is_file() or apk.is_symlink() or not receipt.is_file() or receipt.is_symlink():
        raise DeviceError("APK and inspection JSON must be existing regular non-symlinked files")
    if receipt.stat().st_size > 65536:
        raise DeviceError("oversized inspection receipt")
    try:
        r = json.loads(receipt.read_text())
    except (json.JSONDecodeError, UnicodeError) as exc:
        raise DeviceError("invalid APK inspection receipt") from exc
    if not isinstance(r, dict):
        raise DeviceError("receipt is not an object")
    schema = r.get("schema")
    if schema == "dustui.devtool.m04.apk-inspection.v1":
        package = "com.mikeyphw.dustui.debug"
        cert = r.get("debug_signer_certificate_sha256")
        expected_status = "DEBUG_APK_LOCAL_BYTES_VERIFIED"
    elif schema == "dustui.m05.release.v1":
        package = "com.mikeyphw.dustui"
        cert = r.get("signer_sha256")
        expected_status = "SIGNED_RELEASE_LOCAL_BYTES_VERIFIED_PENDING_DEVICE"
    else:
        raise DeviceError("unknown or unreviewed APK inspection schema")
    if (r.get("status") != expected_status or r.get("application_id") != package or
        r.get("version_code") != 1400 or r.get("verified_v2_signed") is not True or
        r.get("device_qualified") is not False or r.get("release_qualified") is not False or
        not re.fullmatch(r"[a-f0-9]{64}", str(cert or "")) or
        not re.fullmatch(r"[a-f0-9]{40}", str(r.get("source_sha") or "")) or
        not re.fullmatch(r"[a-f0-9]{40}", str(r.get("devtool_sha") or ""))):
        raise DeviceError("inspection receipt has wrong package, signature, source or authority fields")
    _, fields = inspect_zip(apk)
    for key in ("apk_sha256", "apk_size", "go_runtime_sha256", "go_runtime_size", "native_abi"):
        if r.get(key) != fields[key]:
            raise DeviceError("imported APK does not match source-bound inspection: " + key)
    return {"source_sha":r["source_sha"], "devtool_sha":r["devtool_sha"],
            "apk_sha256":fields["apk_sha256"], "apk_size":fields["apk_size"],
            "go_runtime_sha256":fields["go_runtime_sha256"], "package":package,
            "certificate_sha256":cert, "version_code":1400,"kind":"release" if schema=="dustui.m05.release.v1" else "debug",
            "ci_run_id":r.get("github_run_id"), "inspection_schema":schema}


def sdk_binary(name: str) -> str:
    folder = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    exe = Path(folder) / "build-tools/36.0.0" / name if folder else None
    if exe is not None and exe.is_file() and not exe.is_symlink():
        return str(exe)
    candidate = shutil.which(name)
    if not candidate:
        raise DeviceError(name + " is required for device acceptance (Android build-tools, not Gradle)")
    return candidate


def command(argv: list[str], timeout: int = 80, allow_failure: bool = False) -> subprocess.CompletedProcess:
    try:
        p = subprocess.run(argv, text=True, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise DeviceError("ADB/SDK tool unavailable or timed out: " + str(exc)[:160]) from exc
    if p.returncode and not allow_failure:
        raise DeviceError("ADB/SDK command failed: " + " ".join(argv[:4]) + " (exit " + str(p.returncode) + ")")
    return p


def verify_signature(apk: Path, expected: dict) -> None:
    signer = command([sdk_binary("apksigner"), "verify", "--print-certs", "--verbose", str(apk)]).stdout
    certs = re.findall(r"(?im)^Signer #([0-9]+) certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", signer)
    if certs != [("1", expected["certificate_sha256"])] or not re.search(r"(?mi)^Verified using v2 scheme.*:\s*true\s*$", signer):
        raise DeviceError("local APK signature does not match the reviewed GitHub receipt")
    badging = command([sdk_binary("aapt2"), "dump", "badging", str(apk)]).stdout
    identity = re.search(r"(?m)^package: name='([^']+)' versionCode='([0-9]+)'",badging)
    if not identity or identity.groups() != (expected["package"], "1400"):
        raise DeviceError("APK package identity or version mismatch")


def adb(serial: str, *argv: str, timeout: int = 60, allow_failure: bool = False) -> subprocess.CompletedProcess:
    return command(["adb", "-s", serial, *argv], timeout=timeout, allow_failure=allow_failure)


def run_device(apk: Path, expected: dict, serial: str, allow_replace: bool) -> dict:
    if not re.fullmatch(r"[A-Za-z0-9_.:-]{3,96}", serial):
        raise DeviceError("explicit, bounded device serial required")
    listing = command(["adb", "devices"]).stdout.splitlines()
    states = [l.split() for l in listing[1:] if l.strip()]
    if not any(len(p)>=2 and p[0]==serial and p[1]=="device" for p in states):
        raise DeviceError("device serial not connected and authorized")
    boot = adb(serial, "shell", "getprop", "sys.boot_completed").stdout.strip()
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abi").stdout.strip()
    if boot != "1" or abi != "arm64-v8a":
        raise DeviceError("device must be boot-complete Android ARM64")
    pkg = expected["package"]
    installed_before = adb(serial,"shell","pm","path",pkg,allow_failure=True).stdout.strip()
    if installed_before and not allow_replace:
        raise DeviceError("package already exists: explicit --allow-replace required for app update")
    # Android's package manager enforces signer continuity on -r. Do not uninstall
    # an existing package or clear app data as a workaround.
    install = ["install", "-r" if installed_before else "-t", str(apk)]
    installed = adb(serial,*install,timeout=180)
    if "Success" not in installed.stdout:
        raise DeviceError("ADB did not confirm APK installation")
    path = adb(serial,"shell","pm","path",pkg).stdout.strip()
    if not path.startswith("package:"):
        raise DeviceError("package is not visible after installation")
    details = adb(serial,"shell","dumpsys","package",pkg).stdout
    if not re.search(r"\bversionCode=1400\b",details):
        raise DeviceError("installed versionCode does not match APK receipt")
    activity = "com.mikeyphw.dustui.MainActivity"
    launched = adb(serial,"shell","am","start","-W","-n",f"{pkg}/{activity}",timeout=45).stdout
    if "Status: ok" not in launched:
        raise DeviceError("Android launch did not report Status: ok")
    pid = adb(serial,"shell","pidof",pkg,allow_failure=True).stdout.strip()
    if not re.fullmatch(r"[0-9]+(?:\s+[0-9]+)*",pid):
        raise DeviceError("app process is not running after launcher start")
    return {"installed_package":pkg,"installed_version_code":1400,"device_serial":serial,
            "device_abi":abi,"install_status":"PASS","launch_status":"PASS","process_status":"PASS",
            "binder_integration":"NOT_RUN","sqlite_end_to_end":"NOT_RUN","ui_navigation":"NOT_RUN",
            "instrumentation":"NOT_RUN","root_worker":"NOT_RUN","privileged_cleanup_safe_mode":"NOT_RUN"}


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=["plan","run"])
    p.add_argument("--apk",required=True,type=Path)
    p.add_argument("--inspection",required=True,type=Path)
    p.add_argument("--serial",default="")
    p.add_argument("--allow-install",action="store_true")
    p.add_argument("--allow-replace",action="store_true")
    p.add_argument("--output",type=Path)
    args=p.parse_args(argv)
    try:
        info=receipt_check(args.apk,args.inspection)
        report={"schema":"dustui.m05.device.v1","status":"SOURCE_BOUND_APK_IMPORT_ONLY",
                **info, "device_qualified":False,"release_qualified":False,
                "independently_authenticated_attestation":False}
        if args.mode=="run":
            if args.output and (args.output.exists() or args.output.is_symlink()):
                raise DeviceError("device evidence output already exists; no device changes performed")
            if not args.allow_install or not args.serial or not args.output:
                raise DeviceError("installation requires run, --allow-install, --serial and new --output")
            verify_signature(args.apk,info)
            report.update(run_device(args.apk,info,args.serial,args.allow_replace))
            report["status"]="ADB_INSTALL_LAUNCH_SMOKE_VERIFIED_INCOMPLETE"
        else:
            if args.allow_install or args.allow_replace:
                raise DeviceError("read-only plan must not request installation")
        if args.output:
            out=args.output.absolute()
            if out.exists() or out.is_symlink():
                raise DeviceError("device evidence must not overwrite existing file")
            out.parent.mkdir(parents=True,exist_ok=True)
            out.write_text(json.dumps(report,sort_keys=True,indent=2)+"\n")
        print(json.dumps(report,sort_keys=True))
        return 0
    except (DeviceError,APKError,ValueError,OSError,KeyError) as exc:
        print("DUSTUI_M05_DEVICE_REFUSED: "+str(exc)[:340],file=sys.stderr)
        return 2


if __name__=="__main__":
    raise SystemExit(main())
