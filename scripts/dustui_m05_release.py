#!/usr/bin/env python3
"""M05 restricted hosted release: source guard, existing-key admission, and signed-APK inspection.

No keystore generation, signing in PRs, device claim, or local Android Gradle execution.
This is separate from Devtool's deliberately debug-only hosted Android provider.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from dustui_m04_verify_apk import inspect_unit_tests, inspect_zip, APKError

REPO = "Mikeyphw/dustui"
TARGET = "dustui_hosted_release"
JOB = "release_build"
TASKS = [":app:testReleaseUnitTest", ":app:lintRelease", ":app:assembleRelease"]
APK = "android/app/build/outputs/apk/release/app-release.apk"
PACKAGE = "com.mikeyphw.dustui"
VERSION = "1400"
DEVTOOL_SHA = "b164463db51e2cffd12f753043b9e55a08cc43ae"
GO_ENTRY = "lib/arm64-v8a/libdustui_go.so"


class ReleaseError(ValueError):
    pass


def fingerprint(value: str) -> str:
    value = str(value or "").replace(":", "").strip().lower()
    if not re.fullmatch(r"[a-f0-9]{64}", value):
        raise ReleaseError("expected signer SHA-256 must be a 64-hex certificate digest")
    return value


def root_identity(root: Path, hosted: bool) -> str:
    root = root.resolve()
    source = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if subprocess.run(["git", "-C", str(root), "diff", "--quiet", "HEAD", "--"], check=False).returncode:
        raise ReleaseError("tracked checkout differs from the exact commit")
    if hosted:
        env = os.environ
        if (env.get("GITHUB_ACTIONS") != "true" or env.get("GITHUB_REPOSITORY") != REPO or
            env.get("GITHUB_EVENT_NAME") != "workflow_dispatch" or
            env.get("GITHUB_REF") != "refs/heads/main" or
            env.get("GITHUB_SHA") != source or
            env.get("GITHUB_ACTOR", "").lower() != "mikeyphw" or
            env.get("RUNNER_OS") != "Linux" or env.get("RUNNER_ARCH") != "X64" or
            env.get("GITHUB_WORKSPACE") != str(root) or
            not re.fullmatch(r"[1-9][0-9]*", env.get("GITHUB_RUN_ID", "")) or
            not re.fullmatch(r"[1-9][0-9]*", env.get("GITHUB_RUN_ATTEMPT", ""))):
            raise ReleaseError("release requires approved manual default-branch GitHub owner/host/commit context")
    return source


def validate_native_config(root: Path) -> dict:
    import tomllib
    cfg = tomllib.loads((root / ".devtool.toml").read_text())
    target = cfg["targets"].get(TARGET, {})
    jobs = target.get("jobs", {})
    job = jobs.get(JOB, {})
    if (target.get("runner") != "command" or target.get("root") != "android" or
        target.get("execution_environment") != "auto" or set(jobs) != {JOB} or
        target.get("workflows", {}).get("ci") != ["job:" + JOB] or
        job.get("runner") != "command" or job.get("root") != "." or
        job.get("command") != ["python3", "../scripts/dustui_m05_release_job.py"] or
        job.get("outputs") != [APK] or
        job.get("metadata", {}).get("resource_class") != "gradle" or
        job.get("metadata", {}).get("global_reservations") != ["android-gradle"]):
        raise ReleaseError("native release job configuration drift; refusing unreviewed Gradle invocation")
    if TARGET in [x.get("target") for x in cfg.get("wrapper", {}).get("commands", {}).values()]:
        raise ReleaseError("release target must not be available from local wrapper")
    return cfg


def tool_run(argv: list[str], *, timeout: int = 120) -> str:
    try:
        completed = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=True)
        return completed.stdout
    except (OSError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as exc:
        raise ReleaseError("required Android/JDK tool failed: " + str(exc)[:250]) from exc


def _java_property(value: str) -> str:
    if not value or any(c in value for c in "\x00\n\r") or len(value) > 1000:
        raise ReleaseError("missing, invalid or oversized signing credential")
    # Java .properties escaping (including leading space and separator characters).
    return (value.replace("\\", "\\\\").replace(" ", "\\ ")
            .replace(":", "\\:").replace("=", "\\=").replace("#", "\\#").replace("!", "\\!"))


def prepare(root: Path) -> dict:
    source = root_identity(root, hosted=True)
    validate_native_config(root)
    e = os.environ
    expected = fingerprint(e.get("DUSTUI_EXPECTED_SIGNER_SHA256", ""))
    alias = e.get("DUSTUI_KEY_ALIAS", "")
    if not re.fullmatch(r"[a-zA-Z0-9_.-]{1,64}", alias):
        raise ReleaseError("keystore alias has invalid characters")
    password = e.get("DUSTUI_STORE_PASSWORD", "")
    key_pass = e.get("DUSTUI_KEY_PASSWORD", "")
    for secret in (password, key_pass):
        _java_property(secret)
    secret_b64 = e.get("DUSTUI_KEYSTORE_BASE64", "")
    if not 200 <= len(secret_b64) <= 500_000:
        raise ReleaseError("existing PKCS12 keystore secret unavailable or too large")
    try:
        contents = base64.b64decode(secret_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ReleaseError("keystore secret is not valid base64") from exc
    if not 128 <= len(contents) <= 400_000:
        raise ReleaseError("decoded keystore size outside reviewed bounds")
    temp = Path(e["RUNNER_TEMP"]).resolve()
    folder = temp / "dustui-m05-private"
    if folder.is_symlink() or folder.exists():
        raise ReleaseError("signing private staging must not preexist")
    folder.mkdir(mode=0o700)
    store = folder / "signing.p12"
    props = folder / "android-signing.properties"
    with store.open("xb") as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(contents)
    # keytool -storepass:env avoids putting the password into argv or logs.
    cert_dump = tool_run(["keytool", "-list", "-v", "-storetype", "PKCS12",
                          "-keystore", str(store), "-alias", alias,
                          "-storepass:env", "DUSTUI_STORE_PASSWORD"])
    found = re.search(r"(?im)^\s*SHA256:\s*([0-9A-F:]{80,100})\s*$", cert_dump)
    if not found or fingerprint(found.group(1)) != expected:
        raise ReleaseError("existing keystore certificate differs from pinned signing fingerprint")
    props_text = ("storeFile=" + _java_property(str(store)) + "\n"
                  "storePassword=" + _java_property(password) + "\n"
                  "keyAlias=" + _java_property(alias) + "\n"
                  "keyPassword=" + _java_property(key_pass) + "\n")
    with props.open("x", encoding="latin-1") as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(props_text)
    out = {"schema": "dustui.m05.signer-preflight.v1", "status": "EXISTING_KEY_VERIFIED",
           "source_sha": source, "certificate_sha256": expected,
           "private_properties_path": str(props), "release_qualified": False}
    # Do not log paths to material if unnecessary; workflow supplies path explicitly.
    print(json.dumps({k: v for k, v in out.items() if k != "private_properties_path"}, sort_keys=True))
    return out


def verify_release(root: Path, output: Path) -> dict:
    source = root_identity(root, hosted=True)
    validate_native_config(root)
    expected_cert = fingerprint(os.environ.get("DUSTUI_EXPECTED_SIGNER_SHA256", ""))
    apk = root / APK
    if not apk.is_file() or apk.is_symlink():
        raise ReleaseError("missing or symlinked reviewed release APK")
    _, payload = inspect_zip(apk)
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise ReleaseError("Android SDK tools not available")
    tools = Path(sdk) / "build-tools/36.0.0"
    aapt2, apksigner = tools / "aapt2", tools / "apksigner"
    for exe in (aapt2, apksigner):
        if not exe.is_file() or exe.is_symlink():
            raise ReleaseError("required pinned Android SDK verification tool missing")
    badging = tool_run([str(aapt2), "dump", "badging", str(apk)])
    match = re.search(r"(?m)^package: name='([^']+)' versionCode='([0-9]+)'", badging)
    if not match or match.groups() != (PACKAGE, VERSION):
        raise ReleaseError("signed APK package/version differs from reviewed release identity")
    signature = tool_run([str(apksigner), "verify", "--verbose", "--print-certs",
                          "--min-sdk-version", "26", str(apk)])
    certs = re.findall(r"(?mi)^Signer #([0-9]+) certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", signature)
    if (certs != [("1", expected_cert)] or
        not re.search(r"(?mi)^Verified using v2 scheme.*:\s*true\s*$", signature)):
        raise ReleaseError("release signer identity or APK v2 signature is invalid")
    tests = inspect_unit_tests(root / "android/app/build/test-results/testReleaseUnitTest")
    if (output.exists() or output.is_symlink() or not output.is_absolute() or
            output.parent.resolve() != (Path(os.environ["RUNNER_TEMP"]).resolve() / "dustui-m05-public")):
        raise ReleaseError("release evidence must be a new runner-temp/public file")
    report = {"schema": "dustui.m05.release.v1", "status": "SIGNED_RELEASE_LOCAL_BYTES_VERIFIED_PENDING_DEVICE",
              "source_sha": source, "devtool_sha": DEVTOOL_SHA, "github_run_id": os.environ["GITHUB_RUN_ID"],
              "github_attempt": int(os.environ["GITHUB_RUN_ATTEMPT"]),
              "application_id": PACKAGE, "version_code": int(VERSION), "signer_sha256": expected_cert,
              "verified_v2_signed": True, "release_unit_tests": tests,
              **payload, "device_qualified": False, "release_qualified": False,
              "independently_authenticated_attestation": False}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps(report, sort_keys=True))
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["plan", "prepare", "verify"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        root = args.root.resolve()
        if args.mode == "plan":
            sha = root_identity(root, hosted=False)
            validate_native_config(root)
            print(json.dumps({"schema":"dustui.m05.release-plan.v1", "status":"REVIEWED_HOSTED_ONLY",
                              "source_sha":sha, "tasks":TASKS, "output":APK, "release_qualified":False},sort_keys=True))
        elif args.mode == "prepare":
            prepare(root)
        elif args.mode == "verify":
            if not args.output:
                raise ReleaseError("verification requires --output")
            verify_release(root, args.output)
        return 0
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError, APKError) as exc:
        print("DUSTUI_M05_RELEASE_REFUSED: " + str(exc)[:420], file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
