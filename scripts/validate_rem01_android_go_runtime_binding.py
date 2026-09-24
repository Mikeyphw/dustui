#!/usr/bin/env python3
from pathlib import Path
import json
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def t(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


fail: list[str] = []
gradle = t("android/app/build.gradle.kts")
bridge = t("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
main = t("go/cmd/dustui-go/main.go")

for token in [
    "buildGoAndroidRuntime",
    "libdustui_go.so",
    'GOOS", "android',
    'GOARCH", "arm64',
    'CGO_ENABLED", "0',
    "-buildmode=pie",
    "jniLibs.srcDir",
]:
    if token not in gradle:
        fail.append("gradle missing " + token)

# REM-02 extends the REM-01 launch contract with an app-private SQLite path.
# Check semantic launch tokens rather than the obsolete single-line constructor.
for token in [
    "nativeLibraryDir",
    "ProcessBuilder(",
    '"serve"',
    '"--db"',
    '"go-state"',
    "maxFrameBytes",
    "stale_or_mismatched_runtime_response",
]:
    if token not in bridge:
        fail.append("bridge missing " + token)

for token in [
    "func serve(databasePath string) error",
    "SessionProtocolName",
    "MaxFrameBytes",
    "OpenSQLiteStore(databasePath)",
]:
    if token not in main:
        fail.append("server missing " + token)

try:
    status = json.loads(t("migration/ledger/ov14_legacy_retirement_status.json"))
except Exception as exc:
    status = {}
    fail.append(f"invalid OV14 truth ledger: {exc}")

if status.get("observed", {}).get("android_go_runtime_binding") != "production":
    fail.append("truth ledger does not record production Android Go binding")
if "go_android_runtime_binding_missing" in status.get("blockers", []):
    fail.append("runtime binding blocker still present")
folded=set(status.get("folded_in",[]))
rem05_folded="rem05_legacy_surface_release_truth_retirement" in folded
if status.get("status") != ("closed" if rem05_folded else "reopened_blocked"):
    fail.append("REM-01 successor Gate D status mismatch")

# Go execution is an EXO target-local job. Keeping it there avoids duplicated,
# environment-sensitive dependency fetches inside a static contract validator.
try:
    cfg = tomllib.loads(t(".devtool.toml"))
    go_job = cfg["targets"]["dustui_android"]["jobs"]["go_test"]
    if go_job.get("root") != "../go":
        fail.append("dustui_android go_test job must run from ../go")
    if go_job.get("command") != ["go", "test", "./..."]:
        fail.append("dustui_android go_test job must execute go test ./...")
    gate_d = cfg["targets"]["dustui_android"]["workflows"].get("gate_d", [])
    if "job:go_test" not in gate_d:
        fail.append("Gate D must retain job:go_test")
except Exception as exc:
    fail.append(f"invalid Devtool Go test job contract: {exc}")

if fail:
    for item in fail:
        print("REM-01 failure:", item, file=sys.stderr)
    raise SystemExit(1)

print("REM-01 Android Go runtime binding PASS; Go execution remains delegated to EXO job:go_test")
