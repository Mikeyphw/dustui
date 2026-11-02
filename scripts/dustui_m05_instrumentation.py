#!/usr/bin/env python3
"""Source-guarded GitHub-only signed release/instrumentation pairing.

Neither a device runner nor a release qualification claim. Never runs Gradle on Termux.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from dustui_m05_release import root_identity, fingerprint, ReleaseError, DEVTOOL_SHA

TARGET = "com.mikeyphw.dustui"
TEST_PKG = TARGET + ".test"
TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
SRC_APK = "android/app/build/outputs/apk/androidTest/release/app-release-androidTest.apk"
APP_APK = "android/app/build/outputs/apk/release/app-release.apk"
ALLOWLIST = [
    "com.mikeyphw.dustui.go.M05ReleaseReadOnlyInstrumentedTest",
    "com.mikeyphw.dustui.go.GoChildLaunchIsolationInstrumentedTest",
    "com.mikeyphw.dustui.ui.AdaptiveRouteContinuityInstrumentedTest",
]


def guard(root: Path) -> Path:
    root_identity(root, hosted=True)
    if os.environ.get("DUSTUI_M05_INSTRUMENTATION_BUILD") != "true":
        raise ReleaseError("reviewed release-only instrumentation build flag not set")
    temp = Path(os.environ["RUNNER_TEMP"]).resolve()
    private = temp / "dustui-m05-private"
    props = private / "android-signing.properties"
    if (os.environ.get("DUSTUI_ANDROID_SIGNING_PROPERTIES") != str(props)
            or not props.is_file() or props.is_symlink() or props.stat().st_mode & 0o077):
        raise ReleaseError("missing or unsafe GitHub signing properties")
    return private


def call(cmd: list[str], *, capture: bool = False, timeout: int = 240, cwd: Path | None = None):
    p = subprocess.run(cmd, text=True, capture_output=capture, check=False, timeout=timeout, cwd=cwd)
    if p.returncode:
        raise ReleaseError("reviewed hosted task failed (exit %d): %s" % (p.returncode, cmd[0]))
    return p.stdout if capture else ""


def build(root: Path):
    guard(root)
    python = Path(os.environ["RUNNER_TEMP"]) / "devtool-venv/bin/python"
    if not python.is_file():
        raise ReleaseError("reviewed Devtool virtual environment missing")
    call([str(python), "-m", "devtool", "--repo", str(root),
          "--target", "dustui_hosted_release", "invoke", "ci"], timeout=5100)
    launcher = root / "android/gradlew"
    if not launcher.is_file() or launcher.is_symlink():
        raise ReleaseError("missing reviewed Gradle wrapper")
    call([str(launcher), "--no-daemon", ":app:assembleReleaseAndroidTest"], timeout=4200, cwd=root / "android")
    if not (root / SRC_APK).is_file():
        raise ReleaseError("AGP did not produce reviewed release instrumentation APK")
    print("M05_RELEASE_AND_ANDROIDTEST_BUILT_ON_GITHUB_NOT_DEVICE_QUALIFIED")


def signing_sha(text: str) -> str:
    certs = re.findall(r"(?mi)^Signer #([0-9]+) certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", text)
    if len(certs) != 1 or certs[0][0] != "1" or not re.search(r"(?mi)^Verified using v2 scheme[^\n]*:\s*true\s*$", text):
        raise ReleaseError("APK missing sole signer or cryptographic v2 verification")
    return certs[0][1].lower()


def sign(root: Path) -> dict:
    private = guard(root)
    expected = fingerprint(os.environ.get("DUSTUI_EXPECTED_SIGNER_SHA256", ""))
    store = private / "signing.p12"
    if not store.is_file() or store.is_symlink() or store.stat().st_mode & 0o077:
        raise ReleaseError("existing PKCS12 keystore missing or unsafe")
    alias = os.environ.get("DUSTUI_KEY_ALIAS", "")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", alias):
        raise ReleaseError("signing alias missing or invalid")
    for v in ("DUSTUI_STORE_PASSWORD", "DUSTUI_KEY_PASSWORD"):
        if not os.environ.get(v):
            raise ReleaseError("required signing credential missing")
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ["ANDROID_HOME"])
    apksigner = sdk / "build-tools/36.0.0/apksigner"
    aapt2 = sdk / "build-tools/36.0.0/aapt2"
    if any(not p.is_file() or p.is_symlink() for p in (apksigner, aapt2)):
        raise ReleaseError("reviewed SDK verification commands missing")
    public = Path(os.environ["RUNNER_TEMP"]) / "dustui-m05-public"
    receipt = public / "release-receipt.json"
    doc = json.loads(receipt.read_text())
    if (doc.get("schema") != "dustui.m05.release.v1" or
        doc.get("source_sha") != os.environ["GITHUB_SHA"] or
        doc.get("github_run_id") != os.environ["GITHUB_RUN_ID"] or
        doc.get("release_qualified") is not False or
        fingerprint(doc.get("signer_sha256", "")) != expected):
        raise ReleaseError("current hosted release receipt is not exact bound candidate")
    app = root / APP_APK
    test_in = root / SRC_APK
    test_out = public / "app-release-androidTest-signed.apk"
    if (not test_in.is_file() or test_in.is_symlink() or test_out.exists() or test_out.is_symlink()):
        raise ReleaseError("test APK unavailable or signed output already exists")
    # Android Gradle signs androidTest with a debug certificate by default.
    # Re-sign with the EXACT existing production certificate; never generate a key.
    call([str(apksigner), "sign", "--ks", str(store), "--ks-type", "PKCS12",
          "--ks-key-alias", alias, "--ks-pass", "env:DUSTUI_STORE_PASSWORD",
          "--key-pass", "env:DUSTUI_KEY_PASSWORD", "--out", str(test_out), str(test_in)])
    test_cert = signing_sha(call([str(apksigner), "verify", "--verbose", "--print-certs", str(test_out)], capture=True))
    app_cert = signing_sha(call([str(apksigner), "verify", "--verbose", "--print-certs", str(app)], capture=True))
    if app_cert != expected or test_cert != expected:
        raise ReleaseError("test and release certificates differ")
    badging = call([str(aapt2), "dump", "badging", str(test_out)], capture=True)
    pkg_match = re.search(r"(?m)^package: name='([^']+)'", badging)
    inst_match = re.search(r"(?m)^instrumentation: name='([^']+)' targetPackage='([^']+)'", badging)
    if not pkg_match or pkg_match.group(1) != TEST_PKG or not inst_match or inst_match.groups() != (TEST_RUNNER, TARGET):
        raise ReleaseError("instrumentation identity, runner or tested application mismatch")
    app_hash = hashlib.sha256(app.read_bytes()).hexdigest()
    test_hash = hashlib.sha256(test_out.read_bytes()).hexdigest()
    if app_hash != doc.get("apk_sha256"):
        raise ReleaseError("release receipt / current app bytes mismatch")
    result = {"schema":"dustui.m05.signed-instrumentation-pair.v1", "status":"HOSTED_SIGNED_PAIR_PENDING_DEVICE_TESTS",
              "source_sha":os.environ["GITHUB_SHA"], "github_run_id":os.environ["GITHUB_RUN_ID"],
              "github_attempt":int(os.environ["GITHUB_RUN_ATTEMPT"]), "devtool_sha":DEVTOOL_SHA,
              "release_apk_sha256":app_hash, "test_apk_sha256":test_hash,
              "release_package":TARGET, "test_package":TEST_PKG, "runner":TEST_RUNNER,
              "certificate_sha256":expected,"test_classes":ALLOWLIST,
              "device_qualified":False,"release_qualified":False,"privileged_mutations_performed":False,
              "independently_authenticated_attestation":False}
    out = public / "instrumentation-pair.json"
    if out.exists():
        raise ReleaseError("pair receipt already exists")
    out.write_text(json.dumps(result, indent=2, sort_keys=True)+"\n")
    print(json.dumps(result, sort_keys=True))
    return result


def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("build","sign"))
    p.add_argument("--root",type=Path,default=Path(__file__).resolve().parents[1])
    a=p.parse_args(argv)
    try:
        root=a.root.resolve()
        build(root) if a.mode=="build" else sign(root)
        return 0
    except (OSError,ValueError,subprocess.TimeoutExpired,subprocess.CalledProcessError,KeyError) as exc:
        print("DUSTUI_M05_INSTRUMENTATION_REFUSED: "+str(exc)[:380],file=sys.stderr)
        return 2

if __name__=="__main__":
    raise SystemExit(main())
