#!/usr/bin/env python3
"""Independent M04 debug-APK package/arm64/signer inspection, not release proof.

Executed on GitHub after native Devtool test+lint+assemble. Uses aapt2 and
apksigner from the reviewed Android build-tools 36.0.0 installation.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

PACKAGE = "com.mikeyphw.dustui.debug"
VERSION_CODE = "1400"
GO_ENTRY = "lib/arm64-v8a/libdustui_go.so"
OUTPUT = "android/app/build/outputs/apk/debug/app-debug.apk"

class APKError(ValueError):
    pass


def sha256(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def elf_arm64_pie(blob: bytes) -> None:
    if len(blob) < 64 or blob[:4] != b"\x7fELF" or blob[4:7] != b"\x02\x01\x01":
        raise APKError("packaged Go runtime must be ELF64 little-endian")
    etype, arch = struct.unpack_from("<HH", blob, 16)
    if (etype, arch) != (3, 183):
        raise APKError(f"Go runtime must be arm64 PIE ET_DYN/EM_AARCH64, got {(etype, arch)}")


def inspect_zip(apk: Path) -> tuple[bytes, dict]:
    if not apk.is_file() or apk.is_symlink() or apk.stat().st_size < 1024:
        raise APKError("debug APK is missing, symlinked or implausibly small")
    try:
        with zipfile.ZipFile(apk) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise APKError("APK contains duplicate ZIP entry names")
            for entry in archive.infolist():
                name = entry.filename
                if (not name or name.startswith("/") or "\\" in name or
                    ".." in name.split("/") or name.startswith(".git/") or
                    ((entry.external_attr >> 16) & 0o170000) == 0o120000):
                    raise APKError("APK contains unsafe archive entries")
            if "AndroidManifest.xml" not in names or not any(re.fullmatch(r"classes(?:[0-9]+)?\.dex", n) for n in names):
                raise APKError("APK requires Android manifest and classes.dex")
            if GO_ENTRY not in names:
                raise APKError("APK does not contain bundled arm64 Go runtime")
            other_abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and len(n.split("/")) >= 3 and n.split("/")[1] != "arm64-v8a"})
            if other_abis:
                raise APKError(f"unreviewed APK native ABIs: {other_abis}")
            broken = archive.testzip()
            if broken:
                raise APKError("invalid APK ZIP CRC entry: " + broken)
            runtime = archive.read(GO_ENTRY)
            elf_arm64_pie(runtime)
            payload = apk.read_bytes()
            return payload, {"go_runtime_sha256": sha256(runtime), "go_runtime_size": len(runtime),
                             "apk_sha256": sha256(payload), "apk_size": len(payload),
                             "entries": len(names), "native_abi": "arm64-v8a"}
    except (zipfile.BadZipFile, OSError, EOFError) as exc:
        raise APKError("APK failed ZIP integrity inspection: " + str(exc)) from exc


def inspect_android_tools(apk: Path, sdk_root: Path) -> dict:
    build_tools = sdk_root / "build-tools" / "36.0.0"
    aapt2, apksigner = build_tools / "aapt2", build_tools / "apksigner"
    if not build_tools.is_dir() or build_tools.is_symlink() or any(not x.is_file() or x.is_symlink() for x in (aapt2, apksigner)):
        raise APKError("reviewed SDK build-tools 36.0.0/aapt2/apksigner are unavailable")
    for cmd in ([str(aapt2), "dump", "badging", str(apk)],
                [str(apksigner), "verify", "--verbose", "--print-certs", "--min-sdk-version", "26", str(apk)]):
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120, check=True)
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
            raise APKError("SDK APK verification failed: " + str(exc)[:300]) from exc
        if cmd[0] == str(aapt2):
            badging = result.stdout
        else:
            signature = result.stdout
    match = re.search(r"(?m)^package: name='([^']+)' versionCode='([0-9]+)'", badging)
    if not match or match.groups() != (PACKAGE, VERSION_CODE):
        raise APKError("debug APK application ID or versionCode mismatch")
    certs = re.findall(r"(?mi)^Signer #([0-9]+) certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", signature)
    if len(certs) != 1 or certs[0][0] != "1" or not re.search(r"(?mi)^Verified using v2 scheme.*:\s*true\s*$", signature):
        raise APKError("debug APK must have a verified v2 signature and one signer")
    return {"application_id": PACKAGE, "version_code": int(VERSION_CODE),
            "debug_signer_certificate_sha256": certs[0][1].lower(), "verified_v2_signed": True}


def inspect_unit_tests(folder: Path) -> dict:
    if not folder.is_dir() or folder.is_symlink():
        raise APKError("Android unit-test XML report directory missing")
    xmls = sorted(folder.glob("TEST-*.xml"))
    if not 1 <= len(xmls) <= 200:
        raise APKError("Android JUnit XML report count must be 1..200")
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for xml in xmls:
        if not xml.is_file() or xml.is_symlink() or xml.stat().st_size > 4_000_000:
            raise APKError("unsafe or oversized Android JUnit report")
        blob = xml.read_bytes()
        if b"<!DOCTYPE" in blob.upper() or b"<!ENTITY" in blob.upper():
            raise APKError("JUnit XML entities and DOCTYPE are forbidden")
        try:
            suite = ET.fromstring(blob)
        except ET.ParseError as exc:
            raise APKError("malformed JUnit XML: " + str(exc)) from exc
        if suite.tag != "testsuite":
            raise APKError("JUnit XML must have testsuite root")
        testcases = suite.findall("testcase")
        tests = len(testcases)
        try:
            declared = int(suite.attrib["tests"])
        except (KeyError, TypeError, ValueError) as exc:
            raise APKError("JUnit testsuite missing integer test count") from exc
        if declared != tests:
            raise APKError("JUnit testcase count and declared count differ")
        counts["tests"] += tests
        counts["failures"] += sum(bool(t.findall("failure")) for t in testcases)
        counts["errors"] += sum(bool(t.findall("error")) for t in testcases)
        counts["skipped"] += sum(bool(t.findall("skipped")) for t in testcases)
    if counts["tests"] <= counts["skipped"] or counts["failures"] or counts["errors"]:
        raise APKError("Android unit-test reports must contain executed, passing tests")
    counts["report_files"] = len(xmls)
    return counts


def inspect(apk: Path, sdk_root: Path, source_sha: str, graph_sha: str, devtool_sha: str, test_results: Path) -> dict:
    for name, value in (("source_sha", source_sha), ("graph_sha256", graph_sha), ("devtool_sha", devtool_sha)):
        if not re.fullmatch(r"[0-9a-f]{64}" if name == "graph_sha256" else r"[0-9a-f]{40}", value):
            raise APKError(f"{name} must be an exact digest")
    _, fields = inspect_zip(apk)
    fields.update(inspect_android_tools(apk, sdk_root))
    fields["android_unit_tests"] = inspect_unit_tests(test_results)
    return {"schema": "dustui.devtool.m04.apk-inspection.v1", "status": "DEBUG_APK_LOCAL_BYTES_VERIFIED",
            "source_sha": source_sha, "graph_sha256": graph_sha, "devtool_sha": devtool_sha,
            **fields, "release_qualified": False, "device_qualified": False,
            "independently_authenticated_attestation": False}


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--apk", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--test-results", required=True)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--graph-sha", required=True)
    p.add_argument("--devtool-sha", required=True)
    args = p.parse_args(argv)
    try:
        if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_SHA") != args.source_sha:
            raise APKError("M04 package verifier requires exact GitHub source context")
        sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        if not sdk:
            raise APKError("Android SDK path is missing")
        apk = Path(args.apk).resolve(strict=True)
        expected = (Path(os.environ["GITHUB_WORKSPACE"]) / OUTPUT).resolve()
        if apk != expected:
            raise APKError("APK path must equal reviewed Android debug output")
        test_results = Path(args.test_results).resolve()
        expected_test_results = (Path(os.environ["GITHUB_WORKSPACE"]) / "android/app/build/test-results/testDebugUnitTest").resolve()
        if test_results != expected_test_results:
            raise APKError("JUnit report location differs from reviewed Gradle task")
        report = inspect(apk, Path(sdk), args.source_sha, args.graph_sha, args.devtool_sha, test_results)
        out = Path(args.output)
        if not out.is_absolute() or out.exists() or out.is_symlink():
            raise APKError("receipt must be a new absolute path")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
        print(json.dumps(report, sort_keys=True))
        return 0
    except (APKError, OSError, KeyError, ValueError) as exc:
        print("DUSTUI_M04_APK_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
