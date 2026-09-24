# DustUI Android → Go Cutover Remediation Roadmap

This roadmap is inserted between the truth-corrected OV14 and the original OV15
Gate-E final qualification. It is deliberately split so production authority is
moved once, not hidden behind compatibility fallbacks or dual writes.

## REM-01 — Real Android Go runtime packaging and session bridge

- add an Android/arm64 Go runtime entrypoint implementing the bounded framed
  session protocol already modeled in `go/internal/runtime`;
- build/package the runtime from Gradle/Devtool as a production artifact and
  prove its exact path/hash inside the APK/install;
- add the Kotlin process/session client with readiness handshake, sequence,
  request/generation identity, death detection, timeout/cancellation, and
  restart/reconcile behavior;
- keep C++/Room authority unchanged in this overlay so the bridge can be proven
  independently;
- EXO evidence must include runtime start, protocol handshake, death, stale
  response rejection, and APK/runtime identity.

## REM-02 — Go-owned SQLite implementation and one-way Room import

- implement actual durable SQLite persistence behind the Go state/schema
  contracts using an Android-compatible, pinned driver/toolchain;
- create schema/migration/open/transaction/recovery behavior, WAL/locking policy,
  corruption/error handling, and bounded retention;
- import Room v3 state one-way where required; no dual-write authority;
- prove restart/process-death durability and parity fixtures before switching
  reads.

## REM-03 — Scan/query/history Android shell cutover

- route Overview/Files/history/search/pagination/storage summaries through the Go
  runtime and Go SQLite authority;
- preserve partial/truncated/stale semantics, sparse/hard-link accounting,
  cancellation, generation identity, and latest-wins behavior;
- after behavioral parity, remove Room/legacy scanner read authority rather than
  leaving a fallback switch.

## REM-04 — Privileged cleanup/mutation cutover and C++ retirement

- route review manifest, capability admission, root mutation, verification,
  cancellation, and reconcile through the Go privileged/mutation protocols;
- prove path replacement, descendant-after-review, mount crossing, replay,
  expiry, runtime death, transport loss, and retained-copy invalidation;
- remove `NativeDustScanner`, `dustui_scanner.cpp`, CMake wiring, and every legacy
  destructive fallback only after those adversarial cases pass.

## REM-05 — Apps, automation, diagnostics, and thin-shell convergence

- connect package/storage evidence, duplicate/residue intelligence,
  WorkManager/Tasker/notification terminal truth, diagnostics/problem evidence,
  privacy retention, and support export to the Go runtime;
- reduce Android to lifecycle/UI/platform adapters plus accessibility and
  navigation semantics; no duplicate domain truth.

## REM-06 — Structural purge and Gate-D reclose

- remove Room domain authority and any no-longer-needed Room dependencies after
  one-way import is complete;
- remove the Python compatibility target/product launch paths from supported
  workflows (archive fixtures only if still useful);
- converge Android navigation to the six semantic product routes and remove
  legacy top-level debug/certification/deletion-report authority;
- retire generic app-local release/install/certification truth in favor of
  Devtool EXO evidence, retaining only Dust-specific semantic gates;
- make `scripts/validate_ov14_retirement_truth_hotfix.py --gate` pass with zero
  blockers, then rerun full Gate D with real Android validation.

## OV15 — Gate E final adversarial/release seal (only after REM-06)

Run the original Gate-E promise: full Go tests, race/fuzz/failure injection,
realistic-scale fixtures, Android Debug/Release unit/lint/build, required device
instrumentation/rooted gates, signing/certificate identity, install/launch/smoke,
exact Go-runtime/APK hash binding, Dust semantic gates, final migration audit,
and final promise-ledger audit. No `--affected` shortcut is permitted for the
final seal.

Required final machine truth:

```text
runtime=go-native-android
android_ui=Kotlin/Compose
engine_database=Go-owned SQLite
python_runtime=none
cpp_scanner_runtime=none
room_domain_authority=none
legacy_destructive_executor=none
legacy_navigation=none
open_migration_promises=0
```
