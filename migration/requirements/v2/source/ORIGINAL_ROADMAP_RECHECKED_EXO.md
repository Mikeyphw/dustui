# DustUI Go Rebuild Roadmap — rechecked repository + EXO edition

**Status:** authoritative implementation roadmap after three-way audit against `DUSTUI_AUTHORITATIVE_MERGED_AUDIT_GO_UIUX_RECHECKED.md`, the current DustUI repository snapshot, and Devtool 1.1.0/EXO. Supersedes the previous roadmap.  
**Program:** full DustUI migration to a Go-native engine/runtime with a thin Kotlin/Compose Android shell, integrated remediation of the full bug-hunt ledger, Devtool convergence, and a Dust-specific UI/UX remodel informed by BKX.  
**Roadmap shape:** 15 DustUI overlays, 5 milestone validation gates, one blocking Devtool EXO prerequisite, plus two non-DustUI ecosystem sidecars tracked separately.  
**Primary repository:** `~/Code/dustui`  
**Primary Android target after OV01:** `dustui_android`  
**Primary Go target after OV01:** `dustui_go`

---

## 1. Program outcome

The campaign is complete only when DustUI has:

- one authoritative Go engine;
- one Go-owned SQLite state model;
- one versioned app ↔ engine/root protocol;
- one durable operation model for scan, analysis, cleanup, automation, recovery, and history;
- one `ScanAuthority` / evidence-health model driving action gates;
- one exact review → capability → execute → verify mutation path;
- one Go implementation used by normal and privileged execution;
- a thin Kotlin/Compose Android shell that owns Android-only APIs and presentation, not domain truth;
- a task-first DustUI UI whose route identity is stable across phone/tablet;
- one Go CLI **and Go TUI** sharing engine semantics with Android, replacing the Python/Textual production TUI;
- no production Python backend;
- no production C++ scanner;
- no independent Kotlin domain engines;
- no final Room/Go dual authority;
- Devtool as the authority for toolchain, build, device, signing, install, artifact, and release evidence;
- Devtool **EXO** as the execution-truth layer for the migration campaign: stable workflow nodes, dependencies/blockers, process ownership, measured progress, diagnostics, artifacts/evidence, durable execution history, and stable run/node identity;
- all ABH-01…ABH-16, RC-01…RC-14, and N-01…N-08 either behaviorally closed, structurally retired, or correctly externalized to Devtool.

This is **not** a port of the old architecture into Go. It is a controlled replacement of the old authority model.

---

# 2. Campaign rules

## 2.1 Overlay rule

Every overlay must be independently reviewable and must contain:

1. schema-v2/current Devtool overlay manifest;
2. checksums and explicit inventory;
3. target declaration;
4. migration-ledger updates;
5. tests for the promises introduced by that overlay;
6. explicit authority-transition notes;
7. explicit legacy paths still reachable after the overlay;
8. explicit legacy paths newly made unreachable;
9. no unrelated cleanup or aesthetic refactors.

Artifact naming:

```text
dustui_go_ov01_contract_devtool_containment_v1.zip
dustui_go_ov02_go_state_protocol_kernel_v1.zip
...
dustui_go_ov15_adversarial_final_release_seal_v1.zip
```

Version suffixes increase only when an overlay itself must be corrected.


## 2.2 EXO-first execution contract

DustUI must **use Devtool EXO as a first-class campaign mechanism**, not merely invoke commands through Devtool.

From OV01 onward:

- `dustui_android` is the product/default target.
- `dustui_go` is a dedicated target rooted at `go/`; this avoids root-level ownership ambiguity with the temporary Python project.
- `dust_tui_legacy` remains a temporary Python compatibility target until Gate D.
- every campaign check runs as a named Devtool **job/workflow node** with a stable ID;
- every workflow node declares explicit `inputs`/`outputs`, or `metadata.read_only = true`;
- workflow dependencies are explicit and preserved as EXO `depends_on`/`blocked_by`;
- safe independent nodes may use `--workflow-parallel`; Gradle concurrency stays bounded (`max_gradle_nodes = 1` unless measured evidence supports a change);
- Android Gradle uses `structured_events = true`, allowing Gradle task/suboperation progress to become typed EXO child execution rather than opaque stdout;
- Go tests use structured Go runner output; Android tests/lint/package/instrumentation use runner actions, not shell wrappers;
- process identity/provenance, diagnostics, artifact/evidence references, transition origin/reason, and measured progress come from EXO;
- HUD/TUI/history/control surfaces use stable `run_id`/`node_id`, not labels or log filenames;
- raw stdout/log files are diagnostic evidence only and never override canonical EXO terminal truth;
- `--affected` may optimize local work, but no milestone gate may use it as the sole coverage authority.

### Fast workflow versus full validation workflow

Non-gate overlays still apply with `--no-validate`, but **semantic checks are not embedded ad-hoc shell validation**. After apply, run the fast EXO workflow, normally:

```text
devtool -r "$REPO" --target dustui_android test --workflow-parallel
```

The fast workflow grows as the migration grows and contains the narrow tests needed by that overlay.

Milestone overlays run the full EXO workflow, normally:

```text
devtool -r "$REPO" --target dustui_android validate --workflow-parallel
```

Release qualification uses Devtool's canonical release operation in addition to the Gate-E validation run; the two EXO runs must be bound by Git identity, Go-runtime digest, APK digest, signing identity, and release evidence.

### Required EXO topology

The exact graph evolves, but it must converge on stable nodes equivalent to:

```text
contracts
├── go-validate
│   └── android-package
│       └── android-instrumentation
├── android-test
├── android-lint
├── domain-semantic-gates
└── migration-audit
```

Additional nodes are added for scanner fixtures, mutation adversarial tests, package evidence, automation, privacy/support evidence, TUI parity, rooted-device qualification, and final release evidence. Do not collapse them back into one `validate.$runner` node.

### EXO evidence acceptance at every milestone gate

A gate is not valid merely because commands returned zero. The campaign must verify the canonical EXO evidence for that run:

- nested EXO execution schema is the current supported schema (v5 in audited Devtool);
- run reaches a terminal canonical status;
- the workflow graph is sealed/final when discovery is complete;
- stable nodes and dependency edges match the planned workflow;
- failed dependencies produce explicit blocked nodes rather than manufactured successes;
- `transition_origin` / `transition_reason` distinguish observed and scheduler-inferred outcomes;
- process ownership/provenance is attached to the correct node;
- direct/recursive/leaf accounting is coherent;
- measured progress remains distinct from resolution progress; no synthetic `100%` may mask failed/unobserved work;
- diagnostics, artifacts, and evidence references are bounded/redacted;
- canonical execution journal/checkpoint/history can be replayed without identity reuse.

This is a campaign acceptance contract, not optional observability.

## 2.3 Validation policy

All **non-gate** overlays apply with `--no-validate`. They still run their own narrow contract/self-tests inside the overlay where appropriate.

Full Devtool validation happens at exactly these milestone overlays:

- **Gate A — OV03**
- **Gate B — OV05**
- **Gate C — OV08**
- **Gate D — OV13**
- **Gate E — OV15 (final)**

The milestone gates validate the complete target state, not only files touched by that overlay.

Because DustUI has overlapping root-level Go/compatibility content and nested Android content, milestone gates must **not rely on `--affected` as the sole coverage mechanism**.

When Gradle tasks are specified to Devtool, each task is passed with its own `--task` flag. No space-joined task blobs.

## 2.4 Audit loop after every overlay

After every overlay, perform a lightweight audit loop before moving on:

- migration ledger still maps every open finding;
- no new dual writer exists;
- no old and new destructive executor can both be authoritative;
- no hidden Python/C++ fallback has been introduced;
- no route derives domain truth locally;
- no accepted/queued/launched state is reported as success;
- no critical publication path gained a fail-open fallback;
- no generic build/device/signing/release responsibility has moved out of Devtool;
- old implementation paths are retired only if replacement behavioral tests exist;
- new findings are added to the ledger rather than silently folded into another item.

At Gate overlays, this becomes a full audit loop against the rechecked audit.


### Source/token contract-test retirement rule

The current repository contains many tests that inspect source text or shell script tokens. They are useful only as temporary migration tripwires.

OV01 must inventory them and assign each one a replacement overlay. For each subsystem:

1. keep the old source/token guard while the legacy implementation remains authoritative;
2. add behavioral/failure-window/instrumentation evidence in the replacement overlay;
3. retire the source/token guard **in that same overlay** once the replacement proves the invariant;
4. OV14 is only the final sweep for leftovers, not the first mass retirement.

Examples:

- release-script token tests → replaced by Devtool plan/release evidence from OV01 onward;
- cleanup/recovery source contracts → replaced by OV07/OV08 capability/mutation failure tests;
- Debug source contracts → replaced by OV12 evidence/diagnostics behavior;
- navigation/UI source contracts → replaced by OV05/OV13 route/instrumentation/accessibility tests.


---


# 2.5 Current-repository baseline locked by this audit

The roadmap is grounded in the current DustUI tree, not an assumed future layout.

Audited current state:

- Python project is `dust-tui` / `dust_tui`, implemented with **Textual** and exposed as `dust-tui`.
- Android currently contains roughly 116 Kotlin sources, a C++ production scanner plus native test, one AIDL root service contract, and a Room v3 domain database.
- Room v3 currently owns `scan_sessions`, `scan_entries`, `cleanup_operations`, `cleanup_plan_items`, plus legacy `quarantine_entries` retained for older-schema readability.
- `DustRootService.kt` uses `Executors.newCachedThreadPool()` and in-memory maps for active scan/duplicate/cleanup state.
- `RootServiceClient.writeCleanupExecutionPlan()` has the known temp-rename → direct-overwrite fallback.
- `ScheduledScanWorker` and Tasker currently treat launch/admission too much like success.
- Android navigation currently exposes a 16-value `DustDestination` model with different compact/wide destination sets and semantic remapping.
- the build currently advertises both `arm64-v8a` and `x86_64`, while the Go migration contract is ARM64-first.
- current Android baseline is compile/target SDK 36, minSdk 26, Java/Kotlin toolchain 21, Gradle 9.7.1, AGP 9.3.1, Kotlin 2.4.10, KSP 2.3.11, Build Tools 36.0.0, NDK 28.2.13676358.
- current release/certification scripts directly invoke `adb`, hard-code Debug tasks, and/or guess APK paths.
- the test tree contains many source/token contract tests around release, recovery, diagnostics, and UI. They are migration guards, not sufficient final behavioral evidence.

These facts are now explicit migration inputs. An overlay that changes one of them must say so.


The current Devtool 1.1.0 source was probed with a canonical three-node parallel workflow:

```text
first ─┐
       ├─ join
second ┘
```

The workflow itself executed correctly: all three EXO nodes succeeded, `depends_on` was preserved, and three processes were attributed. However the persisted canonical EXO snapshot remained:

```text
status = running
history terminal snapshot = absent
```

after the CLI reported success.

This means DustUI **cannot claim EXO-backed milestone truth yet** using the audited Devtool build, even though workflow nodes/process telemetry already work.

Before OV01 is allowed to close, Devtool must fix generic CLI/workflow EXO finalization so normal sequential and parallel workflows:

1. establish explicit run start identity;
2. publish/discover the complete planned workflow graph;
3. emit `GraphSealed` when discovery is complete;
4. emit terminal `RunFinished` exactly once;
5. persist terminal `execution.json`, checkpoint, journal, and history snapshot;
6. preserve blocked-node semantics on dependency failure;
7. preserve `transition_origin` / `transition_reason`;
8. recover/replay terminal runs without identity reuse.

Add direct regression tests for successful, failed, blocked, cancelled, and parallel workflow cases.

**Stop rule:** if this prerequisite is not green through the normal installed `devtool` entrypoint, DustUI may prepare OV01 files but must not advance to Gate A or call EXO evidence authoritative.

# 3. Authority-transition model

The migration is intentionally staged.

| Stage | Scanner/state authority | Destructive authority | UI authority | Legacy status |
|---|---|---|---|---|
| Before OV01 | legacy C++/Kotlin/Room/Python mix | legacy cleanup | old shell | fully reachable |
| OV01–OV04 | legacy production; Go developed against fixtures/shadow state only | legacy, with containment fixes | old shell | reachable but constrained |
| **After OV05** | **Go scanner + Go SQLite become production authority for new scans/read paths** | legacy cleanup remains temporarily reachable only through constrained compatibility path | new shell becomes available; non-destructive areas begin cutover | no new Room scan/domain writes |
| After OV06–OV07 | Go normal/root read/preflight authority | legacy mutation still gated; Go manifest/capability can run review/dry-run | new shell expands | destructive dual authority forbidden |
| **After OV08** | Go | **Go mutation engine becomes sole destructive authority** | Clean/Activity use Go operation truth | legacy destructive executor unreachable |
| OV09–OV12 | Go for package/analysis/automation/diagnostics as each area cuts over | Go | new shell | Kotlin domain engines retired route-by-route |
| **After OV13** | Go | Go | **new route hierarchy + Go authority are product default** | old shell/domain routes unreachable |
| OV14 | Go | Go | new shell | legacy production code physically removed |
| **OV15** | final | final | final | release seal proves no hidden fallback |

**Hard rule:** there is never a period where two engines are allowed to mutate the same authoritative domain state.

Room-v3 migration is explicitly one-way. After a Go domain cutover, Room may remain only as a read/import compatibility source for not-yet-migrated legacy data. No Go↔Room dual-write synchronization is permitted.

---

# 4. Overlay overview

| Overlay | Name | Main outcome | Validation |
|---|---|---|---|
| OV01 | Contract, Devtool/EXO & containment | Freeze promises; create minimal Go target; modernize Devtool/EXO; contain reachable legacy hazards | fast EXO workflow |
| OV02 | Go state/protocol kernel | Expand minimal Go target into SQLite authority model, typed protocol, operation FSM, and Room-import contract | fast EXO workflow |
| OV03 | Scanner/size/ScanAuthority | Go scanner, size truth, scan integrity, history compatibility | **Gate A** |
| OV04 | Query/search/history engine | paged SQL APIs, summaries, search, large-scan performance | fast EXO workflow |
| OV05 | Android Go runtime + shell foundation | first production Go cutover for scan/read paths; new adaptive shell foundation | **Gate B** |
| OV06 | Privileged Go runtime | root protocol, bounds, allowlists, durable reconcile, bounded admission | fast EXO workflow |
| OV07 | Cleanup evidence/capability | proof-bearing evidence, immutable manifest, one-use capability, review UX | fast EXO workflow |
| OV08 | Mutation/recovery cutover | Go becomes sole destructive authority; verify/reconcile/repair-required | **Gate C** |
| OV09 | Package/storage authority | user-aware Android package/storage evidence normalized in Go | fast EXO workflow |
| OV10 | Cleanup intelligence | duplicates, residue, recommendations, rules migrate to Go | fast EXO workflow |
| OV11 | Automation + Activity ownership | WorkManager/Tasker/notifications observe the same Go operation | fast EXO workflow |
| OV12 | Diagnostics/evidence/privacy | evidence health, problem graph, persistent diagnostics, retention, support bundle | fast EXO workflow |
| OV13 | Go CLI/TUI parity + Android semantic cutover | Go CLI + Go TUI; new shell/Go authority become product default | **Gate D** |
| OV14 | Retirement purge | delete C++, Python backend, Room/domain duplication, old shell and dead tests | fast EXO workflow |
| OV15 | Adversarial qualification + final seal | failure injection, rooted/device/scale/accessibility/release qualification | **Gate E / final** |

---

# 5. Detailed overlay plan

## OV01 — Contract, Devtool convergence, and legacy containment

**Artifact:** `dustui_go_ov01_contract_devtool_containment_v1.zip`  
**Maps to audit:** GO00 + GO01 foundation; N-01, N-02, N-07, N-08; stop-ship rules.

### Purpose

Make the migration measurable before changing architecture, fix Devtool so every later overlay is evaluated against the correct target/variant, and patch only those legacy hazards that are too dangerous to leave reachable during migration.

### Required implementation

Create a versioned migration area:

```text
migration/
  ledger/
  contracts/v1/
  fixtures/v1/
  audit/v1/
  archive/
```

Add a machine-readable ledger mapping:

- ABH-01…ABH-16;
- RC-01…RC-14;
- N-01…N-08;
- CP-01…CP-02 as ecosystem sidecars;
- GO phase;
- overlay;
- closure mode;
- closure evidence.

Freeze valid legacy fixtures/oracles without freezing known-bad behavior.

Inventory:

- all Android destinations;
- all deep links;
- all scan entry points;
- all destructive entry points;
- all Tasker/WorkManager/notification entry points;
- all Python/C++/Kotlin domain engines;
- all Room/domain tables;
- all release/certification scripts;
- all raw ADB callers;
- all authoritative file publishers.

Create a **minimal non-authoritative Go skeleton under `go/` in OV01**, not OV02, so `dustui_go` is a real target from the first canonical Devtool/EXO workflow. It needs only version/readiness identity, a trivial command entry point, and fast tests; no production authority moves yet.

Modernize `.devtool.toml`:


- explicit `[workspace].default_target`;
- canonical `runner`/`platform` identity;
- replace stale `ubuntu_provisioner` with the correct Dust CLI/compatibility identity or remove it;
- create `dustui_go` target;
- keep `dustui_android`;
- declare inputs/outputs;
- declare meaningful jobs/workflows with stable EXO node IDs/dependencies;
- define `dust_tui_legacy` as a temporary Python/Textual compatibility target;
- use semantic variant-aware Android tasks;
- preserve proven Termux memory/worker tuning initially;
- return provider selection to `auto` unless a measured exception is documented;
- set `structured_events = true` for Gradle so EXO receives typed child-task progress;
- use Devtool Android operations/artifact registry for generic ADB/install/smoke/artifact work;
- define canonical release/signing policy and Dust-specific gate attachment points;
- capture a source-contract-test retirement ledger mapping every source/token test to the behavioral replacement overlay;
- preserve the current stable Android toolchain baseline (SDK 36, minSdk 26, Java 21, Gradle 9.7.1, AGP 9.3.1, Kotlin 2.4.10, KSP 2.3.11, Build Tools 36.0.0) unless an overlay provides measured evidence for changing it;
- record the ABI migration decision: production Go/Android cutover is `arm64-v8a` first. `x86_64` may remain only in a clearly test-only variant with a matching Go runtime; an APK may never advertise an ABI for which the Go engine is absent.

### Mandatory temporary containment while legacy remains reachable

Do **not** fully remediate the old architecture. Add only the stop-ship guards:

- destructive actions reject truncated/degraded/stale/incompatible scans;
- privileged admission becomes bounded; remove unbounded `newCachedThreadPool()` behavior;
- critical atomic publication fails closed instead of direct-overwrite fallback;
- lost destructive transport response never triggers blind replay;
- automation launch/admission is never reported as terminal success;
- Release plans must not resolve to Debug tasks.

### Explicit non-goals

- no Go production engine yet;
- no UI remodel yet;
- no attempt to perfect old Room/C++/Python domain architecture;
- no cleanup-engine rewrite yet.

### Overlay acceptance

- Devtool config doctor is canonical;
- Debug and Release plans resolve to variant-correct tasks;
- EXO sees known write scopes and meaningful jobs rather than one opaque fallback node;
- a fast EXO workflow reaches terminal/sealed canonical truth with dependency/process evidence;
- migration ledger has 100% mapping coverage;
- every legacy destructive entry point is either contained or explicitly blocked;
- no existing Termux tuning is lost without measured justification.

### Closure status

This overlay may close **N-01/N-02/N-07/N-08 configuration defects**, but it does not close the underlying RCs whose legacy implementations remain reachable.

### Validation

Apply with `--no-validate`, then run the **fast EXO workflow**. Its OV01 nodes must include at least:

- migration-contract ledger check;
- Go-skeleton test;
- legacy-containment unit tests;
- Devtool config doctor/plan assertions;
- Debug/Release plan snapshot assertions;
- overlay manifest/checksum self-check;
- migration audit join node.

Debug and Release plans must show semantic task expansion (`testDebugUnitTest`/`lintDebug`/`assembleDebug` versus their Release counterparts), known write scopes, and the intended dependency graph.

---

## OV02 — Go state, SQLite, protocol, and operation kernel

**Artifact:** `dustui_go_ov02_go_state_protocol_kernel_v1.zip`  
**Maps to audit:** GO02 + GO03 core; RC-06 foundation; BKX/chrootctl operation donors.

### Purpose

Establish the Go runtime's durable authority model without cutting production behavior over yet.

### Required implementation

Expand the OV01 Go skeleton into package boundaries for:

```text
engine/
protocol/
state/
operations/
evidence/
scan/
query/
cleanup/
diagnostics/
platform/
cmd/
```

Implement typed errors and stable reason codes.

Define deterministic import/migration from the current Room v3 authority before creating the new final state:

- `scan_sessions`;
- `scan_entries`;
- `cleanup_operations`;
- `cleanup_plan_items`;
- legacy `quarantine_entries` must remain readable/importable according to retention policy, but receive no new writes.

The import is one-way and versioned; there is no long-lived bidirectional synchronization.

Create Go-owned SQLite with at minimum:

```text
scan_sessions
scan_entries
scan_contexts
operations
operation_events
cleanup_manifests
cleanup_capabilities
cleanup_results
package_inventory_snapshots
analysis_results
evidence_generations
problems
run_history
```

Requirements:

- schema versioning;
- WAL or safe equivalent;
- deterministic migrations;
- transaction tests;
- expected-generation/state predicates;
- crash-safe commit behavior;
- no production Room dual writer.

Implement operation states equivalent to:

```text
queued
running
cancelling
success
failure
cancelled
interrupted
unknown
recovery_required
```

Implement:

- request fingerprint;
- monotonic event sequence;
- duplicate-request protection;
- late-event rejection;
- terminal-state stickiness;
- reconciliation hooks;
- bounded event persistence.

Define versioned protocol envelopes for:

- readiness;
- execute/admit;
- status;
- progress/events;
- cancel;
- reconcile;
- query/page;
- diagnostics/evidence.

### Non-goals

- no Android process launch yet;
- no scanner replacement yet;
- no destructive execution;
- no final package/storage model.

### Acceptance

Behavioral tests prove:

- stale event cannot resurrect a terminal operation;
- request ID cannot be reused for a different payload;
- cancellation remains sticky;
- DB migration rollback is safe;
- expected-generation writes reject stale actors;
- corrupt/invalid state becomes blocked/recovery evidence rather than guessed success.

### Validation

`--no-validate`; run the fast EXO workflow with named Go state/protocol/SQLite migration nodes. Go package tests + SQLite migration/failure-window tests are EXO-owned, not ad-hoc post scripts.

---

## OV03 — Go scanner, size semantics, ScanAuthority, and history compatibility

**Artifact:** `dustui_go_ov03_scanner_size_scanauthority_gateA_v1.zip`  
**Maps to audit:** GO04 + GO05; RC-01, RC-02, RC-12; ABH-03/05/07/08.

### Purpose

Make scan truth explicit before Android starts depending on the Go engine.

### Required implementation

Implement the Go scanner with:

- canonical root;
- scan generation;
- backend identity;
- options fingerprint;
- hidden/exclusion policy;
- filesystem-boundary policy;
- observed/persisted counts;
- malformed-event/record accounting;
- truncation;
- integrity state;
- source completeness;
- stale-after-cleanup;
- timestamps.

Implement explicit:

```text
logicalSizeBytes
allocatedSizeBytes
sizeSemanticsUsedForScan
```

with tested sparse-file, hard-link, symlink, mount-boundary and unreadable-entry policies.

Implement `ScanAuthority` capabilities:

```text
canAnalyze
canCompareWith(other)
canDriveDeletion
canDriveDuplicateHashing
canDriveResidue
canExportCompleteData
canShowAsFresh
```

Implement history compatibility based on semantic dimensions, not `scanId`.

Build fixture/oracle comparison against legacy outputs where behavior is meant to remain compatible, and explicit expected differences where old behavior is being corrected.

### Non-goals

- production Android cutover waits for OV05;
- no cleanup execution;
- no Compose rewrite.

### Gate A validation

Full milestone validation must include:

- all Go unit tests;
- scanner filesystem fixtures;
- sparse/hard-link/mount/symlink cases;
- integrity/truncation/malformed cases;
- history compatibility matrix;
- SQLite migration tests;
- Android current-shell compile/unit sanity;
- Devtool Debug/Release plan correctness;
- full audit-loop check for RC/N coverage;
- canonical EXO gate evidence: terminal run, sealed graph, stable dependencies/blockers, process ownership, transition origin/reason, truthful progress/accounting, journal/checkpoint/history replay.

**Gate A exit condition:** the Go engine can represent scan truth more strongly than the legacy engine and is safe to integrate into Android as a read/scan authority.

---

## OV04 — Paged query, search, summary, and history engine

**Artifact:** `dustui_go_ov04_query_search_history_v1.zip`  
**Maps to audit:** GO06; RC-07 plus RC-12 query side; ABH-06/07/08/09/12.

### Purpose

Eliminate whole-scan in-memory materialization before the new UI depends on Go data.

### Required implementation

Go SQL/query APIs for:

- paged explorer children;
- whole-scan search;
- current-folder filtering as a distinct operation;
- sort/filter;
- large files;
- storage summaries;
- composition/treemap aggregates;
- scan history;
- growth/trend series;
- package-linked path lookup;
- Activity list/page preparation.

Requirements:

- stable page tokens or deterministic offset/keyset semantics;
- generation binding;
- explicit total/partial/unknown counts;
- query cancellation;
- bounded response size;
- indexes qualified on realistic large scans;
- no UI-side full-list filtering for full-scan features.

### Acceptance

- million-scale/specified realistic fixture remains bounded in memory;
- search and local filtering are semantically distinct;
- pagination does not duplicate/skip under stable generation;
- stale generation query fails clearly;
- summary values use explicit size semantics.

### Validation

`--no-validate`; targeted Go query/performance tests run as named fast-EXO workflow nodes.

---

## OV05 — Android Go runtime integration and new shell foundation

**Artifact:** `dustui_go_ov05_android_runtime_shell_gateB_v1.zip`  
**Maps to audit:** GO07; RC-01/06/07 UI cutover foundation; BKX runtime/navigation donor.

### Purpose

Make Go the production authority for **new scans and read-only scan-derived state**, and introduce the new DustUI shell without yet changing destructive authority.

### Required implementation

Package the Android/ARM64 Go runtime as an app-owned native artifact under `nativeLibraryDir`.

At this cutover, reconcile the current ABI matrix:

- production ABI contract becomes `arm64-v8a`;
- remove `x86_64` from production `abiFilters` unless a dedicated test-only variant packages a matching x86_64 Go runtime;
- Gate B must run on an ARM64 emulator/device or physical ARM64 device; no production APK may advertise an ABI lacking the Go runtime.

Create persistent Kotlin runtime/session adapter:

- fixed executable path;
- version/readiness handshake;
- bounded stdin/stdout/stderr;
- request IDs and sequence numbers;
- reconnect/reconcile;
- process death detection;
- session ownership;
- normal state namespace.

Perform the versioned Room-v3 → Go-SQLite import for retained scan/history state, including explicit treatment of legacy `quarantine_entries`.

Cut **new scan writes** to Go SQLite. From this overlay onward:

- no new scan/session/entry authority may be written by Room;
- Room may be import/read compatibility only;
- legacy scan data may be migrated/imported deterministically.

Introduce typed semantic routes and new shell foundation:

- Overview;
- Files;
- Clean;
- Apps;
- Activity;
- System;

but document that this grouping is provisional and **not an acceptance requirement**.

Implement:

- stable route identity;
- adaptive nav chrome;
- route restoration;
- deep-link translation;
- route generation/entity binding;
- shared `Loading / Empty / Blocked / Error / Content` states;
- shared typed action-gate rendering;
- global Scan workflow;
- initial Overview/Files/Activity read-only surfaces.

### Authority change

**After OV05, Go is the authoritative production scanner/state engine for all new scans.**

Legacy scanner/Room scan writes become unreachable or migration-only.

Destructive cleanup remains legacy and constrained until OV08.

### Gate B validation

Must include:

- Go ARM64 Android build;
- runtime spawn/readiness/protocol tests;
- process death/reconnect;
- new scan → SQLite → query → Compose path;
- route restoration;
- phone/tablet route-identity tests;
- stale async response rejection;
- instrumentation for Overview/Files/Activity basics;
- no new Room scan writes;
- Android compile/unit/lint through EXO workflow nodes;
- Debug APK assembly/install/smoke through Devtool Android operations;
- Gate-B canonical EXO evidence contract from §2.2.

**Gate B exit condition:** the app can live on Go-owned scan/read state across process recreation with the new route model, without any destructive cutover.

---

## OV06 — Privileged Go runtime and root boundary

**Artifact:** `dustui_go_ov06_privileged_runtime_protocol_v1.zip`  
**Maps to audit:** GO08; RC-05; N-04; BKX/chrootctl privileged-boundary donors.

### Purpose

Create the final privileged execution boundary before cleanup capability/execution is moved.

### Required implementation

Run the same Go domain implementation in privileged mode with a separate root namespace.

The privileged process revalidates:

- protocol version;
- request ID syntax;
- operation allowlist;
- field allowlist;
- capability class;
- payload size;
- response/event size;
- root/runtime identity;
- target scope.

Implement:

- bounded worker pool and bounded queue;
- destructive class globally single-flight;
- explicit admission rejection;
- durable lookup/status/reconcile;
- no arbitrary shell operation;
- no replay after lost transport;
- root readiness/scope diagnostics;
- boot/process identity where useful for reconciliation.

System → Runtime/Modules consumes this authority.

### Non-goals

- no production Go destructive execution until OV08;
- no generic shell console.

### Acceptance

- oversized/unknown-field/unknown-operation requests fail closed;
- unauthorized/wrong-scope requests fail closed;
- lost Binder/session response can be reconciled;
- queue exhaustion is typed, bounded, and non-destructive;
- root process restart does not imply replay.

### Validation

`--no-validate`; protocol fuzz/boundary/root-simulator tests as named fast-EXO workflow nodes.

---

## OV07 — Actionable evidence, immutable cleanup manifest, and single-use capability

**Artifact:** `dustui_go_ov07_cleanup_manifest_capability_v1.zip`  
**Maps to audit:** GO09; RC-03, RC-04 preparation, N-06; BKX/chrootctl capability donors.

### Purpose

Make review evidence cryptographically/structurally bind execution before switching the mutation engine.

### Required implementation

Implement `ActionablePathEvidence` equivalents retaining:

- path;
- scan/generation;
- source surface/kind;
- expected hash/identity;
- retained-copy constraints;
- package/user/bucket;
- protected state;
- duplicate/rule/recommendation IDs;
- deletion eligibility.

Build immutable cleanup manifest with:

- exact descendants;
- path role/protection;
- mount boundary;
- identity evidence;
- duplicate retained-copy constraints;
- manifest hash/revision;
- source context hash;
- root/scope identity.

Build durable one-use capability:

```text
capabilityId
operationId
scanId
scanGeneration
scanContextHash
manifestHash
manifestRevision
rootIdentity
reviewedAt
expiresAt
claimState
```

Implement the redesigned Clean review flow:

- exact paths;
- exact bytes with semantics;
- reasons/provenance;
- warnings/protected descendants;
- explicit acknowledgements where required;
- capability creation.

### Safety rule

OV07 may perform dry-run/preflight against the Go privileged runtime, but **does not make the Go mutation engine the production executor yet**.

### Acceptance

- changing any reviewed identity invalidates authorization;
- adding a descendant after review never expands the approved set;
- capability replay fails;
- expired capability fails;
- lost response after capability claim requires status/reconcile;
- bare paths cannot claim specialized safety labels.

### Validation

`--no-validate`; manifest/capability adversarial tests as named fast-EXO workflow nodes.

---

## OV08 — Go mutation engine, recovery, verification, and destructive cutover

**Artifact:** `dustui_go_ov08_mutation_recovery_gateC_v1.zip`  
**Maps to audit:** GO10; RC-04, RC-05, RC-06; N-03, N-04, N-06; chrootctl mutation donor.

### Purpose

Make Go the **only** production destructive authority.

### Required implementation

Implement mutation FSM with states sufficient for:

```text
reviewed
claimed
admitted
executing
verifying
complete
failed
interrupted
recovery_required
cleanup_debt
```

Implement:

- process-shared/global destructive serialization;
- exact-manifest execution only;
- pre-mutation identity revalidation;
- mount-boundary enforcement;
- protected descendant enforcement;
- retained-copy enforcement;
- no unreviewed recursive expansion;
- crash/interruption journal/state;
- post-mutation verification;
- stale-after-cleanup scan consequence;
- cleanup result evidence;
- reconcile after transport/process loss;
- fail-closed ambiguity.

Activity becomes the canonical user-facing cleanup run view.

### Authority change

**At OV08 cutover, every production destructive path routes to the Go mutation engine.**

Immediately make the legacy destructive executor unreachable. It may remain on disk until OV14 only for rollback archaeology; there is no runtime fallback.

### Gate C validation

Must include:

- manifest tamper;
- path replacement between review and execution;
- symlink/mount crossing;
- protected descendant;
- retained-copy failure;
- capability replay;
- expiry;
- kill root runtime during mutation;
- kill app during mutation;
- lose transport after admission;
- restart/reconcile;
- verification mismatch;
- atomic/failure-window tests;
- global single-flight tests;
- rooted-device fixture cleanup in a controlled test tree;
- Activity progress/recovery instrumentation;
- full audit loop;
- Gate-C canonical EXO evidence contract from §2.2.

**Gate C exit condition:** all production deletion is review-equivalent, single-use, recoverable, and Go-owned.

---

## OV09 — Android package and storage evidence authority

**Artifact:** `dustui_go_ov09_package_storage_model_v1.zip`  
**Maps to audit:** GO11; RC-08; ABH-11/12.

### Purpose

Replace regex/path-guessing package semantics with explicit Android evidence normalized by Go.

### Required implementation

Thin Kotlin Android collector for platform-only APIs:

- package/user inventory;
- installed/archived;
- system app;
- enabled state;
- source/split dirs;
- CE/DE data;
- external/adoptable/secondary storage where observable;
- code/data/cache/native paths where observable;
- collection completeness/degradation reasons.

Go normalizes and versions the snapshot.

Apps UI uses list/detail and truthful `supported / partial / unavailable / blocked` states.

Residue/cleanup eligibility consumes package/user evidence, never path regex alone.

### Acceptance

- multi-user fixtures;
- archived/uninstalled/system/disabled cases;
- partial evidence is not rendered as zero;
- action-time re-evaluation exists for package-sensitive deletion.

### Validation

`--no-validate`; package-model unit/instrumentation fixtures as named fast-EXO workflow nodes.

---

## OV10 — Duplicates, residue, recommendations, and rules in Go

**Artifact:** `dustui_go_ov10_cleanup_intelligence_v1.zip`  
**Maps to audit:** GO12; RC-03, RC-09; ABH-10/11/13.

### Purpose

Move all cleanup intelligence into Go and make every result proof-bearing.

### Required implementation

### Duplicates

- candidate grouping;
- logical-size semantics;
- content/identity verification;
- verification confidence/state;
- canonical/keep selection;
- retained-copy proof;
- actionable evidence output.

### Residue

- package/user evidence;
- action-time re-evaluation;
- ambiguity/confidence;
- special-path context;
- exclusions/protection;
- actionable evidence output.

### Recommendations/rules

- proof-bearing outputs;
- bounded imports;
- bounded/non-pathological regex/pattern execution;
- provenance-preserving unions;
- plan-level conflict detection;
- protected-descendant/retained-copy validation before authorization.

Cut corresponding Clean subroutes to Go.

As each Go engine reaches parity, make its Kotlin business-policy implementation unreachable.

### Acceptance

No specialized result can degrade to a bare path and retain a “verified/safe” label.

### Validation

`--no-validate`; behavioral domain tests + pathological-input limits as named fast-EXO workflow nodes.

---

## OV11 — Automation ownership and Activity convergence

**Artifact:** `dustui_go_ov11_automation_activity_v1.zip`  
**Maps to audit:** GO13; RC-10; ABH-04/15; BKX/XBS Activity donors.

### Purpose

Make UI, WorkManager, Tasker, notifications, and recovery observe the **same durable operation**.

### Required implementation

Create one Go automation admission/ownership path.

WorkManager and Tasker become thin adapters:

```text
request operation
→ receive operationId
→ observe durable operation
→ return/report only terminal truth
```

Rules:

- accepted != success;
- queued != success;
- running != success;
- notification actions carry nonce/expiry/replay guards;
- cancellation is an operation transition;
- process death does not orphan the job.

Complete Activity UX:

- active/history filtering;
- run details;
- progress;
- result/verification;
- recovery-required;
- retry/cancel/reconcile gates;
- operation/evidence correlation.

### Acceptance

Scheduling, Tasker, notification, and UI execution of the same operation produce equivalent terminal semantics.

### Validation

`--no-validate`; WorkManager/Tasker contract tests + Activity instrumentation as named fast-EXO workflow nodes.

**Automation scope for this campaign:** Tasker/WorkManager/notification automation remains non-destructive (`scan`, `status`, `cancel`, and safe inspection). Do not add unattended cleanup/deletion automation without a separate security/UX contract.

---

## OV12 — Evidence health, problem graph, diagnostics, privacy, and support bundle

**Artifact:** `dustui_go_ov12_diagnostics_evidence_privacy_v1.zip`  
**Maps to audit:** GO14; RC-11, RC-14; N-05; XDM/XBS/chrootctl diagnostic donors.

### Purpose

Replace debug-state heuristics with durable, privacy-bounded evidence and user-comprehensible root causes.

### Required implementation

Evidence generations:

```text
missing
stale
current
degraded
```

Derived health invalidates when authoritative evidence advances.

Implement `DustProblemGraph`:

- root-cause suppression;
- dependent symptom relationships;
- stable problem IDs;
- guided remediation;
- remediation risk class.

Implement structured persistent diagnostics:

- session/operation/parent IDs;
- area/severity/action/result;
- redaction before persistence;
- bounded values;
- bounded files/sessions;
- no automatic upload;
- significant-problem records separate from raw events.

Implement privacy-retention matrix for:

- raw paths;
- hashes;
- package names;
- root/device metadata;
- retention;
- pruning coupling;
- export redaction;
- backup/device-transfer eligibility.

Create sealed support bundle:

- manifest;
- checksums;
- selected evidence generations;
- explicit privacy mode;
- no file-content leakage by default.

System → Diagnostics replaces Debug as the canonical UX.

### Acceptance

- `COMPLETED` alone can never produce healthy;
- stale evidence is visibly stale;
- root causes do not duplicate downstream symptoms;
- retention/pruning is deterministic;
- support export is reproducible and privacy-qualified.

### Validation

`--no-validate`; evidence freshness/redaction/retention/bundle tests as named fast-EXO workflow nodes.

---

## OV13 — Go CLI/TUI parity and Android semantic/UI cutover

**Artifact:** `dustui_go_ov13_cli_android_cutover_gateD_v1.zip`  
**Maps to audit:** GO15 + GO16; full UI/UX semantic cutover.

### Purpose

Make the new Go authority and new route hierarchy the default product, while replacing the current Python/Textual host surface with a Go CLI **and Go TUI** that use the same engine semantics rather than becoming second implementations.

### Required implementation

Go CLI/TUI:

- preserve the supported host workflows currently exposed by `dust-tui` (interactive browsing, resize/layout behavior, settings, scan/backend selection, and export where still product-supported);
- expose a Go TUI mode/entry point and a non-interactive CLI mode backed by the same packages;
- keep a compatible `dust-tui` command/alias during migration if practical, but its implementation must be Go after Gate D;
- scan;
- query/search;
- status/activity;
- cleanup review/capability/execution where appropriate;
- diagnostics/evidence;
- machine-readable output;
- same reason/error codes and operation semantics as Android.

Android:

- new route hierarchy is default;
- no production navigation into retired legacy screens;
- no production invocation of Python/Textual for supported host workflows;
- no domain action uses Kotlin-local truth;
- no cleanup action bypasses manifest capability;
- all active/recent operations come from Go;
- all content routes use shared screen states/action gates;
- compact/wide layouts preserve semantic route identity;
- Scan remains a workflow, not mandatory top-level destination;
- top-level destination count is not an acceptance criterion.

Complete the semantic migration for:

- Overview;
- Files;
- Clean;
- Apps;
- Activity;
- System;

or the UX-validated equivalent task-domain grouping.

### Gate D validation

Full pre-purge validation:

- Go CLI + Go TUI parity/smoke tests against frozen supported Python/Textual behavior fixtures;
- Android complete task-flow instrumentation;
- phone/tablet/rotation route tests;
- deep-link tests;
- stale-response tests;
- accessibility labels and traversal;
- all domain actions confirmed Go-authoritative;
- release-variant unit/lint/build tasks;
- rooted runtime smoke;
- Devtool artifact identity;
- Gate-D canonical EXO evidence contract from §2.2.

**Gate D exit condition:** the product no longer needs the old shell or old domain engines for any supported user task.

---

## OV14 — Legacy retirement purge

**Artifact:** `dustui_go_ov14_legacy_retirement_v1.zip`  
**Maps to audit:** GO17; structural closure phase.

### Purpose

Physically remove architectures that are already unreachable after Gate D so they cannot silently return.

### Delete or remove from production

- C++ production scanner;
- independent Python production backend;
- retired Kotlin domain-analysis engines;
- final Room/domain authority and migration-only writers;
- mutable cleanup cache authorization;
- NDJSON production state-transfer tailing;
- old 16-destination/remapping shell;
- legacy Debug/Certification/Deletion Reports top-level routes;
- generic release/build/install logic now owned by Devtool;
- any remaining stale source-string tests that merely assert implementation tokens. Most must already have been retired earlier, **immediately after** the corresponding behavioral/failure-injection replacement became authoritative;
- compatibility flags/fallbacks whose retirement gate has passed.

Keep only deliberately archived fixtures/contracts under migration archives.

### Required proof

Static/runtime audit must prove:

```text
cpp_scanner_runtime = none
python_runtime = none
room_domain_authority = none
legacy_destructive_executor = none
legacy_navigation = none
```

### Validation

`--no-validate`; compile/static reachability checks plus retirement audit as named fast-EXO workflow nodes.

---

## OV15 — Adversarial qualification and final release seal

**Artifact:** `dustui_go_ov15_adversarial_final_release_seal_v1.zip`  
**Maps to audit:** GO18 + GO19; RC-13 final closure; all unresolved ledger items.

### Purpose

Prove the replacement under realistic failure, scale, device, accessibility, and release conditions.

### Required adversarial qualification

At minimum:

- process death during scan;
- process death during cleanup;
- root runtime death;
- transport loss after destructive admission;
- restart/reconcile;
- route change while response is in flight;
- rotation/window resize;
- stale scan after cleanup;
- wrong root/module scope;
- malformed/incomplete/truncated scan;
- capability replay;
- expired capability;
- manifest tamper;
- descendant appears after review;
- path identity replacement;
- mount crossing;
- duplicate retained-copy invalidation;
- very large scan pagination/search;
- sparse/hard-link size fixtures;
- multi-user package/residue;
- Tasker/WorkManager terminal truth;
- notification stale-action replay;
- privacy retention/pruning;
- support-export privacy modes;
- accessibility traversal;
- destructive confirmation/back behavior;
- deep link to deleted/stale entity;
- Release build/install/smoke with exact Go runtime/APK binding.

Replace weak source-token tests with behavioral/failure-window tests where the old tests were acting as safety evidence.

### Final machine-readable migration audit

Must report at least:

```text
runtime = go-native-android
android_ui = Kotlin/Compose
engine_database = Go-owned SQLite
python_runtime = none
cpp_scanner_runtime = none
room_domain_authority = none
legacy_destructive_executor = none
legacy_navigation = none
open_migration_promises = 0
```

### Gate E / final Devtool validation

Run complete Go + Android validation and canonical Devtool release qualification:

- full Go tests;
- race/fuzz/failure-injection suites where supported;
- realistic-scale performance fixtures;
- Android Debug and Release unit/lint/build;
- instrumentation on required device/emulator set;
- rooted device gates;
- artifact/signing/certificate identity;
- install/launch/smoke;
- exact runtime binary hash bound into release evidence;
- Dust-specific semantic qualification gates;
- final migration audit;
- final promise-ledger audit;
- canonical EXO validation-run evidence satisfies §2.2;
- canonical Devtool release EXO run is cross-bound to the validation run by Git identity, Go-runtime digest, APK digest, signing identity, and Dust semantic gate evidence;
- no raw `adb`, manually guessed APK path, or shell-computed generic release truth remains authoritative;
- no `--affected` shortcut.

**Final exit condition:** every ledger entry has evidence and there is no hidden legacy production fallback.

---

# 6. Validation gates in detail

## Gate A — OV03: engine semantics gate

Proves:

- Devtool is trustworthy enough to run the campaign;
- Go state/protocol/scanner foundations are coherent;
- size semantics and scan truth are explicit;
- history compatibility is semantic.

No production authority cutover is required yet. Gate A is also the first mandatory proof that DustUI's real multi-target validation graph is represented and finalized correctly by EXO.

## Gate B — OV05: Android runtime/read-authority gate

Proves:

- app-owned Go runtime works on Android;
- Go SQLite survives recreation/process death;
- new scans/read paths are Go-authoritative;
- ARM64 runtime/APK ABI contract is coherent;
- route generation prevents stale UI updates;
- adaptive UI changes chrome/density, not route meaning.

## Gate C — OV08: destructive safety gate

Proves:

- Go is the only destructive authority;
- review and execution operate on the same immutable object set;
- capability is single-use;
- ambiguous outcomes reconcile instead of replay;
- cleanup survives crash/restart safely;
- legacy destructive executor is unreachable.

## Gate D — OV13: product semantic cutover gate

Proves:

- all supported Android task flows use the new Go engine;
- CLI and Android share semantics;
- old UI/domain engines and Python/Textual host runtime are unnecessary;
- the app can proceed to physical legacy deletion.

## Gate E — OV15: final release gate

Proves:

- adversarial, scale, privacy, accessibility, rooted-device and release evidence;
- no legacy runtime remains;
- all audit promises close;
- EXO run/history truth is terminal, sealed, replayable, and cross-bound to canonical release evidence;
- Devtool binds exact source/runtime/APK/device/signing evidence.

---

# 7. UI/UX acceptance contract across the roadmap

The roadmap intentionally does **not** lock DustUI to five or six destinations.

The contract is:

1. stable semantic routes;
2. task domains are discoverable;
3. phone/tablet differ in chrome/density, not route meaning;
4. Scan is a workflow available where evidence is needed;
5. specialized Dust features remain first-class:
   - explorer;
   - whole-scan search;
   - treemap/composition;
   - growth/history;
   - large files;
   - duplicates;
   - residue;
   - recommendations;
   - rules;
   - app storage;
   - root/modules;
   - Activity;
   - diagnostics/support;
6. dangerous actions use progressive inspect → review → authorize → execute → verify;
7. `Blocked`, `Empty`, `Error`, `Loading`, and `Content` are distinct;
8. disabled risky actions expose typed reasons/remediation;
9. charts have exact textual/accessibility equivalents;
10. advanced/root detail is available without turning normal UX into a developer console.

The provisional task-domain grouping remains:

```text
Overview
Files
Clean
Apps
Activity
System
```

but UX validation may merge/split/surface them differently without violating the roadmap.

---


# 7.1 Concrete current-tree retirement map

| Current legacy surface | Current evidence in repo | Containment/cutover | Physical retirement |
|---|---|---|---|
| Python/Textual `src/dust_tui` + `dust-tui` | host TUI is production Python | frozen as parity oracle OV01; Go CLI/TUI Gate D | OV14 |
| C++ scanner / CMake | Android APK builds native scanner for arm64+x86_64 | Go scanner semantic Gate A; production scan authority OV05 | OV14 |
| Room v3 scan/domain DB | `DustDatabase.kt`, scan/cleanup tables | versioned import OV02/OV05; no new scan writes after OV05 | per-domain, final OV14 |
| `DustRootService` cached executor/in-memory maps | unbounded privileged admission | bounded containment OV01; Go privileged runtime OV06; mutation cutover OV08 | OV14 |
| `RootServiceClient` cleanup-plan cache file | temp rename with overwrite fallback | fail-closed containment OV01; immutable capability OV07 | authority removed OV08, code purge OV14 |
| raw WorkManager/Tasker launch semantics | worker/receiver launch and accept | terminal ownership OV11 | old semantics removed OV11/OV14 |
| 16-destination `DustDestination` + compact/wide remaps | semantic route changes by layout | route foundation OV05; complete cutover OV13 | OV14 |
| raw ADB/release shell orchestration | scripts call adb, hard-code Debug, guess APK | generic authority moved to Devtool OV01; Dust-only semantic gates retained | generic duplicates OV14 |
| source/token contract tests | recovery/release/debug/UI tests inspect source/scripts | staged behavioral replacement throughout | final leftovers OV14 |
| x86_64 production ABI | current `abiFilters` includes x86_64 | production ARM64 contract enforced OV05 unless matching test-only Go runtime | OV05/product config |

# 8. Finding-to-overlay closure summary

## Root causes

| Finding | Primary overlay(s) | Closure gate |
|---|---|---|
| RC-01 Scan truth | OV03, OV05, OV12 | Gate B / final health proof |
| RC-02 Size semantics | OV03 | Gate A + final fixtures |
| RC-03 Proof flattened to paths | OV07, OV10 | Gate C / final |
| RC-04 Review ≠ execution object set | OV07, OV08 | Gate C |
| RC-05 Weak root/IPC boundary | OV06, OV08 | Gate C |
| RC-06 In-memory safety lifecycle | OV02, OV05, OV08, OV11 | Gate C / D |
| RC-07 Full-scan in-memory scale | OV04, OV05 | Gate B + final scale |
| RC-08 Package/storage incompleteness | OV09 | Gate D/final |
| RC-09 Marketing labels without proofs | OV10 | Gate D/final |
| RC-10 Automation launchers not owners | OV11 | Gate D/final |
| RC-11 Weak diagnostics/cert/release evidence | OV01, OV12, OV15 | Gate E |
| RC-12 Invalid history comparability | OV03, OV04 | Gate A/B |
| RC-13 Weak validation strategy | all; especially OV15 | Gate E |
| RC-14 Privacy retention uncontrolled | OV12, OV15 | Gate E |

## New concrete findings

| Finding | Overlay(s) |
|---|---|
| N-01 Release variant runs Debug tasks | OV01 |
| N-02 Devtool workspace/target/workflow under-modeled | OV01 |
| N-03 fail-open authoritative file publication | OV01 containment; OV02/OV08 final elimination |
| N-04 unbounded root admission | OV01 containment; OV06 final |
| N-05 `COMPLETED == healthy` | OV03/OV12 |
| N-06 weak cleanup publication/replay | OV07/OV08 |
| N-07 release scripts duplicate Devtool | OV01/OV14/OV15 |
| N-08 inconsistent overlay evidence | OV01 onward |

---

# 9. ABH coverage summary

| ABH section | Main overlays |
|---|---|
| ABH-01 Bootstrap/Android integration | OV01, OV05, OV12, OV15 |
| ABH-02 Root service/IPC | OV06, OV08 |
| ABH-03 Scanner/event protocol | OV03, OV05 |
| ABH-04 Scan lifecycle/FGS | OV02, OV05, OV11 |
| ABH-05 DB/sessions/recovery | OV02, OV03, OV08 |
| ABH-06 Explorer/search | OV04, OV05 |
| ABH-07 Home/visual analytics | OV04, OV05, OV13 |
| ABH-08 Growth/history | OV03, OV04, OV13 |
| ABH-09 Large files | OV03, OV04, OV07 |
| ABH-10 Duplicates | OV10 |
| ABH-11 Residue | OV09, OV10 |
| ABH-12 App storage/package intelligence | OV09 |
| ABH-13 Recommendations/rules | OV10 |
| ABH-14 Destructive cleanup | OV07, OV08 |
| ABH-15 Scheduling/Tasker/notifications | OV11 |
| ABH-16 Navigation/shared state/diagnostics | OV05, OV12, OV13, OV15 |

---

# 10. Cross-project prerequisites and sidecars

These remain tracked but are **not counted among the 15 DustUI overlays**.

## ECO-01 — BKX ↔ chrootctl canonical module compatibility

Fix BKX's stale active module resolution from:

```text
/data/adb/modules/chrootctl-magisk
```

to canonical:

```text
/data/adb/modules/chrootctl
```

while retaining the old path only as migration/diagnostic evidence.

Add producer/consumer compatibility fixtures for:

- module ID/path;
- legacy module IDs;
- backend version;
- bridge protocol;
- operation/capability schema.

**Recommended timing:** before OV06 donor-derived privileged-runtime assertions are finalized.

## ECO-02 — XBS/Flowspace Gradle helper convergence

Remove the duplicated large `gradle-build.py` orchestration layer and converge generic host/chroot/Gradle execution on Devtool, leaving thin project-specific wrappers only where necessary.

**Recommended timing:** independent of DustUI critical path; after OV01 proves the desired Devtool target/workflow model.

---

# 11. What must not be “fixed twice”

Once this roadmap begins, avoid spending time on final-form repairs to architecture scheduled for deletion.

Do **not** separately perfect:

- C++ scanner architecture;
- Room as final domain authority;
- Kotlin duplicate/residue/rule engines;
- mutable cleanup-cache authorization;
- screen-owned operation state;
- Python backend parity as a separate implementation;
- raw ADB/release scripts already being replaced by Devtool;
- old 16-destination navigation;
- source-string safety tests once their behavioral replacement exists; until then they remain temporary migration guards.

Patch these only when needed to keep the pre-cutover product safe.

Their original bug findings remain acceptance criteria for the Go replacement.

---

# 12. Definition of done

The roadmap is complete only when:

### Runtime

- Go engine is production authority;
- same Go domain implementation is used for normal/root execution;
- Go SQLite is final durable authority;
- no dual writer;
- no C++ scanner runtime;
- no Python production backend;
- no Kotlin domain authority;
- Go CLI and Go TUI own supported host workflows; Python/Textual runtime is absent.

### Safety

- every destructive action comes from proof-bearing evidence;
- exact immutable manifest is reviewed;
- single-use capability binds manifest/generation/scope;
- execution never expands reviewed scope;
- recovery is durable and ambiguity fails closed;
- lost transport causes reconcile, never blind replay.

### Product

- scan truth is explicit and centrally gated;
- logical/allocated size semantics are explicit;
- explorer/search/large scans are paged;
- package/user evidence is modern and truthful;
- duplicate/residue/recommendation/rule results preserve proofs;
- automation observes terminal operation truth;
- Activity is authoritative;
- diagnostics explain rather than own operational state;
- privacy retention is explicit and bounded.

### UI/UX

- route identity is stable across form factors;
- destination count is not hard-coded as a product requirement;
- all Dust-specific task domains remain discoverable;
- blocked/stale/unsupported/error states are explicit;
- dangerous actions use progressive review;
- accessibility and exact-value alternatives exist.

### Tooling/release

- Devtool Debug/Release planning is variant-correct;
- EXO sees meaningful jobs and known write scopes;
- generic device/install/sign/release truth is Devtool-owned;
- every overlay's fast/full checks execute as Devtool EXO workflow nodes with known scopes;
- every milestone EXO run terminates, seals its graph, persists replayable history, and preserves dependencies/blockers/process provenance;
- every overlay has proper evidence;
- final release evidence binds Git + Go runtime binary + APK + signing + device + semantic gates;
- promise ledger has zero open items.

---

# 13. Recommended execution discipline

For implementation:

1. Build one overlay at a time.
2. Apply non-gate overlays with `--no-validate`.
3. Run the fast Devtool **EXO workflow**; do not replace it with ad-hoc shell validation.
4. Run the audit loop.
5. Do not start the next overlay if a new authority conflict appears.
6. At OV03/05/08/13/15, run the full Devtool EXO validation workflow and verify canonical EXO evidence before the full audit loop.
7. If a gate exposes a design defect, fix the current overlay version rather than carrying known debt forward.
8. After Gate D, legacy code is allowed to exist only as unreachable code pending OV14.
9. After OV14, any reintroduction of a legacy fallback is a release blocker.
10. OV15 is the only overlay allowed to declare the program complete.

---

# 14. Final campaign order

```text
OV01  Contract + Devtool + containment
  ↓
OV02  Go SQLite/protocol/operation kernel
  ↓
OV03  Scanner + size + ScanAuthority ───────────── GATE A
  ↓
OV04  Query/search/history
  ↓
OV05  Android Go runtime + new shell ───────────── GATE B
  ↓
OV06  Privileged Go runtime
  ↓
OV07  Cleanup evidence + manifest capability
  ↓
OV08  Mutation/recovery destructive cutover ────── GATE C
  ↓
OV09  Package/storage authority
  ↓
OV10  Duplicate/residue/recommendation/rules
  ↓
OV11  Automation + Activity
  ↓
OV12  Diagnostics/evidence/privacy/support
  ↓
OV13  Go CLI/TUI + Android semantic/UI cutover ─────── GATE D
  ↓
OV14  Retirement purge
  ↓
OV15  Adversarial qualification + release seal ─── GATE E
```

**This ordering is dependency-sensitive.** In particular:

- do not build the new cleanup executor before scan truth and operation authority exist;
- do not make Go destructive before privileged reconcile and immutable capability exist;
- do not purge legacy implementations before Gate D proves they are unnecessary;
- do not let UI cutover hide unresolved authority conflicts;
- do not let final release qualification become the first place where scale, crash, privacy, or rooted-device behavior is exercised.
