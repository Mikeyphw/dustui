#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    p = root / rel
    if not p.is_file():
        raise SystemExit(f"missing required file: {rel}")
    return p.read_text(encoding="utf-8", errors="replace")

store = read("go/internal/state/sqlite_store.go")
runtime = read("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
worker = read("android/app/src/main/kotlin/com/mikeyphw/dustui/root/GoRootWorker.kt")
policy = read("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoChildProcessPolicy.kt")
policy_test = read("android/app/src/test/kotlin/com/mikeyphw/dustui/go/GoChildProcessPolicyTest.kt")
instrumented = read("android/app/src/androidTest/kotlin/com/mikeyphw/dustui/go/GoChildLaunchIsolationInstrumentedTest.kt")
server = read("go/internal/runtimeio/server.go")
main = read("go/cmd/dustui-go/main.go")
launch = read("go/internal/launchcontract/contract.go")
reentry = read("go/internal/platformcontract/sqlite_reentry_policy_test.go")
deadlock_contract = read("go/internal/deadlockcontract/single_connection_test.go")

errors: list[str] = []
required = {
    "sqlite max-open invariant": (store, "const sqliteAuthorityMaxOpenConnections = 1"),
    "sqlite max-open application": (store, "db.SetMaxOpenConns(sqliteAuthorityMaxOpenConnections)"),
    "sqlite max-idle application": (store, "db.SetMaxIdleConns(sqliteAuthorityMaxOpenConnections)"),
    "sqlite rationale": (store, "Keep one SQLite connection as a correctness invariant"),
    "android request serialization": (runtime, "lock.withLock"),
    "go synchronous dispatch": (server, "payload, err := dispatch(ctx, frame)"),
    "private cwd policy": (policy, "builder.directory(workingDirectory)"),
    "environment clear": (policy, "clear()"),
    "Android-owned GOMAXPROCS": (policy, "Runtime.getRuntime().availableProcessors().coerceAtLeast(1)"),
    "runtime private launch policy": (runtime, "workingDirectory = stateDir"),
    "root-worker private launch policy": (worker, "GoChildProcessPolicy.apply("),
    "Go launch guard": (main, "launchcontract.ValidateAndroidChild()"),
    "Go launch contract command": (main, 'case "launch-contract"'),
    "shell test path guard": (launch, '"/data/local/tests"'),
    "native test path guard": (launch, '"/data/nativetest"'),
    "general DB re-entry analyzer": (reentry, "TestSQLiteAuthorityDoesNotReenterDBBeforeRowsClose"),
    "v2 deadlock contract follows named invariant": (deadlock_contract, "sqliteAuthorityMaxOpenConnections"),
    "unit contaminated TEST_TMPDIR": (policy_test, 'builder.environment()["TEST_TMPDIR"] = "/data/local/tests"'),
    "unit contaminated LD_LIBRARY_PATH": (policy_test, 'builder.environment()["LD_LIBRARY_PATH"] = "/data/local/tests/lib64"'),
    "device packaged child test": (instrumented, "packagedGoChildStartsWithSanitizedPrivateLaunchContract"),
    "device launch contract": (instrumented, '"launch-contract"'),
    "device production serve startup": (instrumented, "GoRuntimeProcess.start(context)"),
}
for label, (text, needle) in required.items():
    if needle not in text:
        errors.append(f"{label} missing")

for name in ("PWD", "HOME", "TMPDIR", "TMP", "TEMP", "PATH", "ANDROID_ROOT", "ANDROID_DATA", "GOMAXPROCS", "DUSTUI_ANDROID_CHILD"):
    if f'"{name}"' not in policy:
        errors.append(f"Go child policy does not pin {name}")

if "go dispatch(ctx, frame)" in server:
    errors.append("runtime dispatch became concurrent without SQLite policy review")
if 'db.SetMaxOpenConns(1)' in store:
    errors.append("anonymous SQLite connection literal reintroduced; use named authority invariant")
if ".redirectError(ProcessBuilder.Redirect.INHERIT)" in runtime:
    errors.append("runtime stderr reverted to inherited process stream")

# Production code may mention the forbidden paths only in the launch guards that
# reject them. No domain/SQLite/runtime implementation should probe them itself.
allowed = {
    root / "go/internal/launchcontract/contract.go",
    root / "android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoChildProcessPolicy.kt",
}
for base in (root / "go/cmd", root / "go/internal", root / "android/app/src/main/kotlin"):
    for path in base.rglob("*"):
        if not path.is_file() or path.name.endswith("_test.go") or path.suffix not in {".go", ".kt"} or path in allowed:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        for needle in ("/data/local/tests", "/data/nativetest", "/sys/fs/cgroup"):
            if needle in source:
                errors.append(f"production source directly probes {needle}: {path.relative_to(root)}")

if errors:
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)
print("sqlite authority / Go child launch-hardening contract: PASS")
