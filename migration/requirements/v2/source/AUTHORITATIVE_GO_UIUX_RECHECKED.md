# DustUI authoritative merged audit — Go migration + UI/UX remodel edition (rechecked)

**Status:** authoritative, reconciled successor to the Go-migration and prior Go+UI/UX merged audits  
**Supersedes:** `DUSTUI_AUTHORITATIVE_MERGED_AUDIT_GO_MIGRATION.md` and the earlier `DUSTUI_AUTHORITATIVE_MERGED_AUDIT_GO_UIUX.md` as the implementation roadmap  
**Product direction:** Go-native engine/runtime with a thin Kotlin/Compose Android shell, plus a task-first DustUI interface remodel informed by BKX without cloning BKX's navigation count or product structure.

---

## 1. Executive decision

DustUI should not separately execute the old bug-hunt remediation, the cross-project donor work, the Go migration, and a later UI redesign. They are now one migration program.

The final product should have:

- one authoritative Go engine and durable Go state model;
- a thin Kotlin/Compose Android shell for Android-only APIs and presentation;
- one versioned app↔engine/root protocol;
- one operation/activity model across scan, analysis, cleanup, automation, and recovery;
- one evidence/health model governing whether UI actions are permitted;
- one navigation hierarchy driven by DustUI's user tasks rather than the historical implementation modules;
- responsive presentation without changing the identity or semantics of a route between phone and tablet;
- destructive actions using inspect → review → authorize → execute → verify, never shortcut paths;
- Devtool as the authority for build/toolchain/device/signing/release evidence.

The BKX influence is therefore **behavioral and architectural**: task-first screens, progressive disclosure, explicit readiness, operation activity, stable nested routes, clear status surfaces, and bounded advanced tooling. DustUI does **not** need BKX's five-destination shell.

**Normative-source rule:** this document controls the final architecture and roadmap. `BUGHUNT_FINAL.md` remains normative for detailed unresolved edge cases and acceptance requirements unless this document explicitly marks a remediation as superseded or an implementation path as structurally retired. Architectural simplification must not erase a bug-hunt invariant.

---

# 2. Final architecture

```text
Android / Compose shell
│
├── Navigation + adaptive presentation
├── Android package/storage APIs
├── permissions / notifications / WorkManager / Tasker
├── Android file/document pickers
└── render-only screen state
        │
        ▼
Versioned Dust Go protocol
│
├── readiness + capabilities
├── query/page
├── operation admission
├── progress + events
├── review/authorize/execute
├── cancel/reconcile
└── diagnostics/evidence
        │
        ▼
Go Dust engine
│
├── scanner + size semantics
├── SQLite authority
├── ScanAuthority
├── operation coordinator
├── files/search/query engine
├── duplicates/residue/rules/recommendations
├── cleanup manifest + single-use capability
├── mutation/recovery engine
├── evidence health + problem graph
├── automation ownership
├── history/activity
└── diagnostics/support evidence
        │
        ├── normal app runtime
        └── privileged/root runtime
```

Kotlin owns presentation and Android integration. Go owns domain truth.

---

# 3. Authority rules

1. **Compose never decides domain truth from incidental local state.** It renders engine state and action gates.
2. **`COMPLETED` never means `healthy` by itself.** Scan/operation health is derived from integrity, generation, completeness, freshness, scope, and verification evidence.
3. **A route never owns an operation.** Operations survive navigation, recreation, and process death.
4. **A UI screen never owns destructive authorization.** Cleanup authorization is a single-use Go capability bound to immutable reviewed evidence.
5. **Responsive layout never changes route identity.** Phone/tablet may render different navigation chrome but must not silently remap one destination to another.
6. **Async UI responses are route/entity/generation bound.** A late response cannot populate a different route or a newer entity.
7. **Activity history is operational truth; diagnostics are explanatory evidence.** The two may correlate by operation ID but are not interchangeable.
8. **Devtool owns external build/release truth.** The app reports Dust-specific qualification gates into that release evidence rather than recreating a release system.

---

# 4. Go migration scope

## 4.1 Go-owned production responsibilities

The Go engine becomes authoritative for:

- filesystem scanning;
- logical and allocated byte accounting;
- scan context, generation, integrity, scope, and comparability;
- durable SQLite state;
- paged entries, search, summaries, sorting, filtering, and history;
- operation lifecycle and recovery;
- cleanup evidence, manifest creation, authorization, execution, and verification;
- duplicate detection/verification;
- residue analysis;
- cleanup recommendations and rules;
- growth/history compatibility semantics;
- evidence freshness and health derivation;
- root-cause problem graph;
- automation operation ownership;
- structured diagnostics and support-bundle inputs;
- CLI behavior shared with the Android product where appropriate.

## 4.2 Kotlin responsibilities retained intentionally

Kotlin/Compose remains responsible for:

- UI rendering and interaction;
- adaptive navigation chrome;
- Android lifecycle integration;
- notifications and foreground-service presentation;
- WorkManager/Tasker adapters;
- PackageManager, StorageManager, SAF and Android permission surfaces;
- deep-link reception and translation to typed Dust routes;
- platform pickers and system settings intents;
- accessibility semantics;
- platform-specific visual state only.

## 4.3 Components scheduled for retirement

After Go cutover and parity gates pass:

- the production C++ scanner is removed;
- Python `dust_tui` ceases to be a separate production backend and becomes/rebases onto the Go CLI or is retired;
- Kotlin domain-analysis implementations are removed;
- Room/domain duplication is removed in favor of Go SQLite authority;
- mutable cleanup-plan cache files cease to be authorization;
- NDJSON tailing ceases to be the Android production state-transfer mechanism;
- screen-specific operation ownership and in-memory lifecycle maps are removed;
- old route-specific data loaders that duplicate Go query semantics are removed.

---

## 4.4 Go persistence and Android runtime non-negotiables

The full-Go requirement is not satisfied merely by moving algorithms into Go. The authoritative runtime shape is part of the migration contract.

### Go-owned SQLite

The final engine database owns, at minimum:

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

- WAL or an equivalently safe concurrent-read mode;
- explicit schema versioning and migration tests;
- transactional expected-generation/state predicates;
- realistic large-scan index/performance qualification;
- crash-safe commit semantics;
- deterministic import/migration from pre-Go Room data where user data must survive;
- **no final dual-writer Room/Go authority**.

Room may exist temporarily as a migration/import adapter, but it is not the final engine authority.

### BKX-style Android Go runtime

Prefer the runtime shape already proven by BKX rather than inventing JNI only for migration convenience:

1. build the Go runtime for Android/ARM64;
2. package the executable as an app-owned native artifact installed under `nativeLibraryDir`;
3. launch that fixed app-owned executable directly rather than an arbitrary shell;
4. keep a persistent session for long-running operations;
5. expose versioned `readiness`, `execute`, `progress`, `cancel`, status/reconcile, and query operations;
6. bind every result/event to request ID plus monotonic sequence;
7. bound request, response, event, stdout, and stderr sizes at both boundaries;
8. scope runtime/session failure to the owning session so stale readers cannot poison a replacement;
9. use distinct normal/root state namespaces and an explicit privileged operation allowlist;
10. run the same Go domain implementation in normal and privileged execution rather than maintaining a second cleanup/scanner implementation.

The current migration target is ARM64 because that matches the present product/device scope; additional ABIs are an explicit product decision, not an accidental build side effect.

### Migration contracts

Create and keep versioned migration evidence such as:

```text
migration/
  contracts/v1/
  fixtures/v1/
  audit/v1/
  archive/
```

Freeze interfaces and valid compatibility promises, **not known bugs**. `COMPLETED == healthy`, wrong size semantics, review/execute mismatch, and automation launch == success are intentional contract corrections and must not be preserved for compatibility.

---

## 4.5 Concrete semantic contracts preserved from the bug hunt

These are normative acceptance contracts. Go types may use different names, but the information and invariants must survive.

### Authoritative scan context

At minimum, the engine needs the equivalent of:

```text
scanId
generation
rootPath
canonicalRoot
backend
optionsFingerprint
sizeSemantics
status
integrityStatus
truncated
observedEntryCount
persistedEntryCount
malformedEventCount
startedAt / completedAt
staleAfterCleanup
sourceCompleteness
```

Capabilities are derived centrally, for example:

```text
canAnalyze
canCompareWith(other)
canDriveDeletion
canDriveDuplicateHashing
canDriveResidue
canExportCompleteData
canShowAsFresh
```

A screen having a scan ID or `COMPLETED` state is never enough by itself.

### Explicit size semantics

Do not keep one ambiguous `sizeBytes` field. Preserve equivalent fields/contracts for:

```text
logicalSizeBytes
allocatedSizeBytes
sizeSemanticsUsedForScan
```

Duplicate hashing/identity primarily needs logical size; storage usage needs allocated size; cleanup identity may require both. The UI must distinguish apparent/logical size from disk usage.

### Proof-preserving actionable-path evidence

The cleanup planner must accept proof-bearing evidence rather than flattening specialized results to paths. The evidence model needs equivalents of:

```text
path
scanId
generation
sourceSurface
sourceKind
expectedSha256?
retainedCopy / retainedCopyHash?
packageName?
userId?
appStorageBucket?
protectedByUser?
duplicateGroupId?
ruleId?
recommendationId?
deletionEligibility
```

Bare generic paths are lowest-trust inputs and must not inherit labels such as “verified duplicate” or “safe residue.”

### Immutable reviewed cleanup manifest

Review produces an immutable exact object set. Required evidence includes, where applicable:

- exact descendants;
- per-entry path role and protection state;
- scan visibility/provenance;
- mount identity/boundary;
- inode/device/mode/link-count/mtime-ns or equivalent identity evidence;
- duplicate retained-copy constraints;
- manifest hash/revision;
- source scan/generation/context hash;
- root/scope identity.

Execution mutates only manifest entries: no unreviewed recursive expansion, no newly appeared descendants, no crossing mount boundaries, and no deleting protected descendants merely because their parent was approved.

### Cleanup capability

The final reviewed-action capability is durable and single-use. It binds at least manifest identity/hash/revision, scan generation/context, privileged scope, expiry, and claim state. A lost response after admission triggers status/reconcile, not replay.

### Package/app-storage evidence

The semantic package inventory needs equivalents of:

```text
packageName
userId
installed
archived
systemApp
enabledState
sourceDir
splitDirs
ceDataDirs
deDataDirs
externalDirs
adoptable / secondary-storage dirs
code/data/cache/native dirs where observable
inventoryCompleteness / degradation reason
```

Deletion/residue eligibility must not be inferred from path regexes alone.

### History comparability

Two scans are comparable only when relevant semantics match, including:

```text
root + canonical root
backend
size semantics
entry mode
min/max size/depth settings
excludes/hidden policy
filesystem-boundary policy
completeness/non-truncation
relevant user/profile scope
```

Volatile identifiers such as `scanId` are not compatibility dimensions.

### Duplicate/residue/recommendation/rule safety

- duplicate verification rechecks content/identity and retained-copy safety;
- residue decisions re-evaluate current package/user evidence at action time;
- recommendations/rules output proof-bearing evidence, not marketing labels + paths;
- rule imports have hard limits;
- regex/pattern execution is bounded/non-pathological;
- unions preserve provenance;
- conflicts are detected at plan level;
- protected-descendant and retained-copy constraints are validated before authorization.

### Automation terminal truth

`accepted`, `queued`, `launched`, and `running` are not success. Tasker, WorkManager, notifications, and UI all observe the same durable operation through a terminal outcome.

### Privacy retention

The engine needs an explicit policy matrix by artifact/evidence class covering:

```text
raw paths
hashes
package names
root/device metadata
retention limit
scan/operation-prune coupling
export redaction
backup/device-transfer eligibility
```

Redaction occurs before persistence/export where applicable, and retained diagnostics/support data are bounded.

---

# 5. UI/UX remodel principles

The UI remodel is not a visual reskin. It is the presentation layer of the new authority model.

## 5.1 Borrow from BKX

Adopt these BKX qualities:

- task-first information architecture instead of implementation-first navigation;
- a useful Home/overview that communicates readiness, active work, recent results, and next actions;
- stable nested routes rather than every workbench becoming a peer navigation destination;
- consistent loading / empty / error / content states;
- prominent operation status and recent activity;
- explicit status pills and plain-language readiness states;
- progressive disclosure for dangerous workflows;
- inspect/review/confirm patterns before mutations;
- a dedicated activity surface rather than scattered progress/history state;
- grouped advanced/system tooling instead of exposing internals as primary navigation;
- route-generation/entity binding so stale async responses cannot mutate the wrong screen;
- deep links that resolve to stable semantic routes.

## 5.2 Preserve DustUI identity

Do **not** flatten DustUI into a backup-app clone. Keep and elevate:

- storage health as the central product concept;
- treemap, donut, bars, and growth visualization;
- filesystem explorer and whole-scan indexed search;
- logical-vs-allocated size semantics;
- large-file analysis;
- exact duplicate analysis;
- app residue analysis;
- cleanup recommendations and cleanup rules;
- app-storage inspection;
- root/module inspection;
- exact-path cleanup review;
- cleanup verification;
- historical storage comparison;
- root diagnostics and support evidence.

DustUI's product personality remains **storage intelligence + safe cleanup**, not generic file management.

---

# 6. Navigation model — domain-led, not destination-count-led

The current implementation exposes 16 historical `DustDestination` values, including a four-item compact shell and a much larger wide navigation list. It also changes destination identity according to layout. That should be retired.

The final model should use stable semantic routes grouped into product areas. The exact visible navigation chrome may differ by form factor, but the route tree does not.

## 6.1 Recommended product areas

A useful current grouping is the six areas below because they match DustUI's present domain boundaries. **This is a provisional information-architecture hypothesis, not a required destination count.** UX testing may merge, split, or surface these areas differently. The contract is stable task domains, stable semantic routes, and no phone/tablet destination substitution.

### Overview

Purpose: answer **"What is using my storage, how trustworthy is this view, and what should I do next?"**

Contains:

- storage-health hero;
- latest scan identity/freshness/integrity;
- analyzed logical/allocated bytes;
- indexed objects;
- cleanup opportunity summary;
- warnings/problems;
- storage composition visualizations;
- treemap/donut launch points;
- trend summary;
- active operation card;
- recent activity;
- best-next-action cards.

Nested routes:

- `overview/home`
- `overview/composition`
- `overview/trends`
- `overview/scan-history`

### Files

Purpose: answer **"Where is the space?"**

Contains:

- explorer;
- breadcrumbs;
- current-folder filter;
- whole-scan search;
- large-file workbench;
- treemap drill-down;
- item details;
- safe multi-selection leading only to reviewed cleanup.

Nested routes:

- `files/explorer`
- `files/search`
- `files/large`
- `files/treemap`
- `files/item/{id}`

### Clean

Purpose: answer **"What can I safely reclaim, and why?"**

Contains:

- ranked cleanup recommendations;
- recoverable byte summary;
- exact duplicates;
- app residue;
- cleanup rules;
- candidate details;
- immutable cleanup review;
- execution progress/verification handoff.

Nested routes:

- `clean/recommended`
- `clean/duplicates`
- `clean/residue`
- `clean/rules`
- `clean/candidate/{id}`
- `clean/review/{manifestId}`

### Apps

Purpose: answer **"Which apps and app-owned areas consume storage?"**

Contains:

- package/app storage list;
- per-package storage breakdown;
- Android storage buckets and special paths;
- cross-user evidence where available;
- module relationships when they are app/module-owned evidence.

Nested routes:

- `apps/list`
- `apps/package/{packageName}`
- `apps/package/{packageName}/paths`

Root module management is not forced into Apps if it is primarily a system/root concern; its canonical route may live under System with contextual links from App detail.

### Activity

Purpose: answer **"What is running, what happened, and what needs recovery?"**

This is the user-facing view of the Go operation coordinator.

Contains:

- active scans;
- active analyses;
- cleanup execution and verification;
- scheduled/automation runs;
- completed/failed/interrupted runs;
- recovery-required operations;
- deletion reports as operation details, not a separate top-level product;
- filters by operation kind/status/time;
- retry/cancel/reconcile actions when allowed;
- correlation to diagnostics/support evidence.

Nested routes:

- `activity/list`
- `activity/run/{operationId}`
- `activity/run/{operationId}/evidence`

### System

Purpose: answer **"Is DustUI's privileged/runtime environment healthy, and how is the product configured?"**

Contains:

- root/runtime readiness;
- scan presets and scan configuration;
- module/root inspection;
- automation/schedules;
- diagnostics/problem graph;
- support bundle export;
- device certification;
- privacy/logging settings;
- appearance/preferences;
- advanced settings.

Nested routes:

- `system/runtime`
- `system/modules`
- `system/automation`
- `system/diagnostics`
- `system/certification`
- `system/settings`

## 6.2 Scan is a workflow, not a navigation destination

`SCAN` should cease to be a permanent destination.

Start Scan should be available from:

- the Overview hero/primary action;
- top-level app action;
- empty states that need scan evidence;
- stale-evidence banners;
- module wrong-scope remediation;
- System → Runtime.

The scan configuration may render as a modal sheet on compact devices and a dialog/side panel/full route on larger displays. Its semantic workflow is the same.

## 6.3 Historical destinations absorbed by the new hierarchy

| Historical destination | Final home |
|---|---|
| `SCAN` | global Scan workflow |
| `EXPLORER` | Files → Explorer |
| `INSIGHTS` | Overview |
| `RECOMMENDATIONS` | Clean → Recommended |
| `RULES` | Clean → Rules |
| `GROWTH` | Overview → Trends |
| `LARGE_FILES` | Files → Large files |
| `DUPLICATES` | Clean → Duplicates |
| `APP_RESIDUE` | Clean → Residue, with contextual links from Apps |
| `SEARCH` | Files → Search |
| `PACKAGES` | Apps |
| `MODULES` | System → Modules |
| `CERTIFICATION` | System → Certification |
| `CLEANUP` / deletion reports | Activity → Run details |
| `DEBUG` | System → Diagnostics |
| `MORE` | removed as a semantic destination; replaced by explicit grouped routes |

This is a structural simplification: it preserves capabilities while removing navigation as an implementation inventory.

---

# 7. Adaptive layout without semantic remapping

The old rule that can map Scan→Insights on compact and More→Insights on wide layouts should be removed.

## 7.1 Compact phones

One reasonable compact chrome option is:

- bottom navigation for whichever high-frequency areas UX validation identifies (the current hypothesis is `Overview`, `Files`, `Clean`, `Activity`);
- overflow/drawer access for lower-frequency areas such as the current `Apps` and `System` grouping;
- global Scan action in the top app bar / Overview hero;
- nested routes preserve their canonical route even if their parent is not permanently visible in the bottom bar.

This is **not** a five-destination product model. It is a compact presentation choice.

## 7.2 Large phones / tablets

Use a rail or adaptive drawer exposing the currently selected top-level product areas directly. If the provisional six-area grouping survives UX validation, all six may be visible; if it changes, the layout follows the validated grouping without changing route semantics.

The content pane can then become two-pane or supporting-pane where useful:

- Files: directory/results + item detail;
- Clean: candidate list + candidate/review detail;
- Apps: package list + package detail;
- Activity: run list + selected run details;
- System: setting/tool list + detail.

## 7.3 Landscape / large tablet

Prefer information-dense master/detail and persistent context rather than adding more navigation destinations.

The wide layout may reveal nested sections in the rail/drawer, but it must not turn every specialized workbench into a top-level peer again.

---

# 8. Route and stale-response safety

BKX's route-key/generation/response-binding idea should be adapted directly.

Each screen request binds to:

```text
routeKey
routeGeneration
entityKind
entityId
requestId
optional query/draft fingerprint
```

A late result is accepted only if all relevant identities still match.

For Dust this is especially important for:

- filesystem search while moving between directories;
- duplicate analysis while changing scan generation;
- residue analysis while package/user scope changes;
- app-storage details while changing package;
- cleanup review while candidates change;
- Activity detail while operation selection changes;
- diagnostics refresh while the underlying evidence generation advances.

The Go protocol should expose enough generation/revision information that this is not implemented as fragile Compose-local heuristics.

---

# 9. Shared screen-state contract

Every content route should expose one consistent presentation state:

```text
Loading
Empty(reason + primary recovery action)
Blocked(reason code + remediation)
Error(problem reference + retry/support action)
Content(data + freshness + action gates)
```

`Blocked` is intentionally distinct from Empty and Error because many DustUI states are valid-but-not-actionable:

- no trusted scan;
- stale after cleanup;
- wrong scan scope;
- root not available;
- scan incomplete/truncated;
- candidate evidence expired;
- cleanup capability already claimed;
- interrupted mutation requires reconciliation;
- package/storage evidence unsupported on this device/user.

This should replace screens that silently render zeroes/empty lists for insufficient evidence.

---

# 10. Visual language

Keep DustUI's existing modern dark/product language, but make it systematic.

## 10.1 Shared primitives

Retain/evolve:

- `DustHeroCard`;
- `DustTaskCard`;
- `DustStatusNotice`;
- `DustMetricTile`;
- `DustActionDock`;
- path pills and selectable monospace paths;
- filter chips;
- bottom-sheet/detail headers;
- status pills;
- section headers;
- storage charts and treemap.

Align their behavior with BKX's consistent surface-card/status patterns, but keep Dust-specific visuals and density.

## 10.2 State color semantics

Color must support, never replace, text labels.

Use stable semantic states:

- healthy/current;
- running;
- attention/stale;
- blocked;
- destructive/irreversible;
- recovery required;
- unknown/unverified.

Avoid treating "completed" as a positive color unless verification/health also passes.

## 10.3 Information density

DustUI should be denser than BKX where the domain demands it. File paths, storage statistics, scan evidence, and candidate comparisons benefit from compact rows and master/detail views.

The remodel should therefore borrow BKX's clarity, **not its exact spacing or card count**.

---

# 11. Core redesigned flows

## 11.1 First launch / no trusted scan

Overview remains usable and explains readiness.

```text
Overview
├── Runtime readiness
├── Storage access readiness
├── "No trusted scan yet"
├── Start scan
└── What a scan enables
```

Do not redirect the user to a Scan destination.

## 11.2 Scan flow

```text
Start scan
  ↓
Choose scope/preset
  ↓
Preflight
  ├── root/runtime identity
  ├── canonical path
  ├── permission/access
  ├── mount/scope evidence
  └── size semantics
  ↓
Review scan request
  ↓
Start
  ↓
Operation appears in Activity + global active-operation surface
  ↓
Completion
  ↓
Health evaluation
  ├── trusted/current → Overview/Files/Clean enabled
  └── degraded → visible problem + limited actions + remediation
```

## 11.3 Files flow

Files defaults to Explorer with an obvious switch to whole-scan Search.

Selection never exposes immediate permanent deletion. It produces an actionable-evidence request which then opens Clean Review.

Large Files is a Files analysis mode, not an unrelated destination.

## 11.4 Cleanup flow

Borrow BKX's strongest progressive-disclosure behavior and combine it with the new Go cleanup capability:

```text
Candidate discovery
  ↓
Inspect evidence
  ↓
Select candidates
  ↓
Build immutable manifest
  ↓
Review exact paths / bytes / reasons / fingerprints
  ↓
Explicit acknowledgement where required
  ↓
Claim single-use cleanup capability
  ↓
Execute
  ↓
Verify
  ↓
Activity run detail + refreshed/stale scan consequence
```

No recommendation card, duplicate row, residue row, file row, rule, notification, or automation bypasses this path.

## 11.5 Duplicates

Duplicates becomes a Clean subroute with clear stages:

- candidate grouping;
- verification state;
- reclaimable bytes;
- canonical/keep candidate;
- exact files;
- selection;
- cleanup review.

Hash confidence/verification state must be explicit.

## 11.6 App residue

Residue is shown as cleanup intelligence, not a binary "junk" label.

Each residue group presents:

- package/user ownership evidence;
- installed/uninstalled ambiguity;
- special-path context;
- confidence;
- exclusion/protection reason;
- paths;
- review action.

## 11.7 App storage

Apps uses a list/detail hierarchy with storage buckets and source evidence. Unsupported or partial buckets show explicit reason states instead of zero values.

## 11.8 Activity

Activity becomes one of the most important new surfaces.

Rows show:

- operation type;
- status;
- start/end time;
- progress if active;
- scope/summary;
- result/verification;
- recovery-required indicator.

Run detail shows:

- operation identity;
- request/evidence generation;
- phases;
- progress counters;
- warnings/errors;
- cleanup verification when relevant;
- safe retry/cancel/reconcile actions;
- correlated diagnostics;
- export/support action.

This absorbs the existing separate deletion-report destination and scattered scan-progress/history UI.

## 11.9 System / Diagnostics

The old Debug Workbench becomes a user-comprehensible Diagnostics surface backed by XDM/XBS-style persistent evidence.

Primary view:

- top root causes from `DustProblemGraph`;
- current runtime/root health;
- scan health;
- evidence freshness;
- actionable remediation;
- support bundle action.

Advanced subsections can expose:

- structured event timeline;
- probes;
- protocol/runtime information;
- module scope evidence;
- raw operation correlation IDs.

Debugging detail remains available without making implementation internals a primary navigation destination.

---

# 12. Global operation UX

Operations must remain visible outside Activity without taking over every screen.

Use a compact global active-operation surface:

- top/hero card on Overview;
- small persistent progress surface/banner where appropriate;
- tap opens Activity run detail;
- cancel shown only when the Go action gate allows it;
- `Cancelling` is not presented as cancelled;
- lost transport is presented as `Reconnecting/Reconciling`, not failed or restarted automatically;
- interrupted/ambiguous mutations become `Recovery required`.

This directly connects BKX's activity clarity with chrootctl-style recovery semantics.

---

# 13. Action gating and disabled-state UX

Every risky or evidence-dependent action must receive a typed gate from the Go engine:

```text
allowed: bool
reasonCode: stable enum/string
message: plain-language explanation
remediationActions: optional typed actions
```

The UI never duplicates gate logic.

Examples:

- `SCAN_REQUIRED`
- `SCAN_STALE_AFTER_MUTATION`
- `SCAN_INTEGRITY_DEGRADED`
- `WRONG_SCOPE`
- `ROOT_UNAVAILABLE`
- `EVIDENCE_EXPIRED`
- `MANIFEST_CHANGED`
- `CAPABILITY_ALREADY_CLAIMED`
- `OPERATION_IN_PROGRESS`
- `RECOVERY_REQUIRED`

Disabled buttons should expose the reason in context; important blocked workflows should show a notice with the remediation action rather than forcing users to discover why a button is disabled.

---

# 14. How the UI remodel changes the original bug-hunt fixes

The original findings remain acceptance requirements, but several UI fixes should no longer be implemented independently.

## 14.1 Structurally absorbed

These old repair patterns become obsolete after the new shell is authoritative:

- compact/wide destination remapping fixes;
- maintaining a 15-item wide navigation rail;
- separate top-level Deletion Reports screen;
- separate top-level Debug Workbench screen;
- separate top-level Certification screen;
- separate top-level Growth screen;
- separate top-level Search route in navigation chrome;
- separate top-level Large Files / Duplicates / Residue / Rules destinations;
- screen-specific stale-scan banners implemented with duplicated rules;
- per-screen progress ownership;
- per-screen ad-hoc readiness derivation;
- snackbar-only reporting for evidence/health problems.

The capabilities remain, but move into stable product areas and shared state policies.

## 14.2 Still mandatory in the new UI

The remodel must preserve or strengthen:

- exact path review before deletion;
- actionable-path evidence;
- stale-after-cleanup blocking;
- root/module wrong-scope explanations;
- search-vs-folder-filter distinction;
- path selection/accessibility;
- large-file pagination/scale;
- duplicate verification confidence;
- residue ambiguity labels;
- app-storage evidence provenance;
- scan truncation/malformed/depth warnings;
- privacy-aware debug/support export;
- explicit size semantics;
- history comparability.

---

# 15. Root-cause reconciliation under Go + UI/UX migration

The original RC identifiers and meanings are immutable audit references. New architecture may supersede a remediation, but must not silently redefine an RC.

Legend:

- **ACTIVE** — original semantic defect remains a requirement until closure evidence exists.
- **ACTIVE / SUBSUMED** — old one-off fixes are replaced by a shared Go authority.
- **ACTIVE / SUPERSEDED REMEDIATION** — the defect remains, but the original Kotlin/C++/Python fix plan is intentionally replaced by the Go architecture.
- **ACTIVE / PARTIALLY EXTERNALIZED** — Dust-specific truth remains in Dust; generic build/release/device truth moves to Devtool.

| Root cause | Status under final plan | Final treatment / owner |
|---|---|---|
| **RC-01 — Scan truth is not a first-class invariant** | **ACTIVE / SUBSUMED** | Go `ScanAuthority`, evidence generation/freshness, centralized action gating; UI renders trusted/degraded/stale reasons |
| **RC-02 — Size semantics are inconsistent across scan, duplicate verification, and cleanup** | **ACTIVE** | Go logical + allocated byte model, hard-link/sparse policy, explicit UI labels |
| **RC-03 — Specialized safety evidence is flattened into bare paths** | **ACTIVE / SUBSUMED** | Go `ActionablePathEvidence` retained into immutable cleanup manifests/capabilities |
| **RC-04 — Cleanup review and native deletion do not operate on an immutable reviewed object set** | **ACTIVE / SUPERSEDED REMEDIATION** | immutable exact descendant manifest, one-use capability, mutation FSM, pre-mutation identity revalidation, global destructive serialization |
| **RC-05 — Root service and IPC boundaries are not strong enough for privileged work** | **ACTIVE / SUPERSEDED REMEDIATION** | typed/bounded Go protocol + thin Kotlin root adapter, allowlists, bounded admission, durable reconcile/status |
| **RC-06 — The app relies on in-memory state for safety-critical lifecycle decisions** | **ACTIVE / SUBSUMED** | Go SQLite operation authority + persistent evidence/history; no per-feature lifecycle islands |
| **RC-07 — Full-scan features are implemented through large in-memory lists and UI-side filtering** | **ACTIVE** | Go SQL paging/search/summary APIs + virtualized Compose/master-detail rendering |
| **RC-08 — Package/app-storage model is incomplete for modern Android** | **ACTIVE** | Android platform evidence snapshot + Go normalized user-aware package/storage model (CE/DE, archive, external/adoptable/secondary/code/data/cache/native where observable) |
| **RC-09 — Recommendations and rules preserve marketing labels, not safety proofs** | **ACTIVE / SUBSUMED** | Go duplicate/residue/recommendation/rule engines emit proof-bearing evidence and plan-level conflict validation |
| **RC-10 — Automation paths are launchers, not owners of the work** | **ACTIVE / SUPERSEDED REMEDIATION** | WorkManager/Tasker submit and observe the same Go operation; queued/accepted never equals terminal success |
| **RC-11 — Diagnostics, certification, and release evidence are not trustworthy enough** | **ACTIVE / PARTIALLY EXTERNALIZED** | Go/XDM/XBS-style evidence + problems/support diagnostics; Devtool owns generic build/sign/device/install/artifact/release truth |
| **RC-12 — Storage history/growth is built on incompatible comparison semantics** | **ACTIVE** | semantic `ScanContext`/options/size compatibility fingerprint and comparison groups in Go |
| **RC-13 — Validation is mostly source-string or tiny-fixture coverage** | **ACTIVE / SUPERSEDED TEST STRATEGY** | behavioral Go/protocol/Compose tests, realistic-scale fixtures, failure injection, rooted/device gates, Devtool workflows |
| **RC-14 — Privacy retention is uncontrolled** | **ACTIVE** | explicit retention/pruning policy + redaction-before-persistence/export + bounded logs/support bundles |

Atomicity/recovery remains mandatory, but it is a cross-cutting **RC-04/05/06/13 implementation requirement**, not a replacement definition for RC-14.

## 15.1 Original ABH section coverage is preserved

| Original section | Required coverage in final program |
|---|---|
| ABH-01 Bootstrap & Android integration | RC-10/11/14; Go runtime bootstrap, Android adapters, Devtool/release/privacy gates |
| ABH-02 Root service & IPC boundary | RC-04/05; typed root protocol, mutation capability, bounded admission/reconciliation |
| ABH-03 Native scanner & scan event protocol | RC-01/02/07/14; Go scanner/protocol replacement, integrity, scale, privacy |
| ABH-04 Scan lifecycle & foreground service | RC-01/06/10; durable Go operation ownership with thin Android FGS presentation |
| ABH-05 Database, sessions & recovery | RC-01/06/13/14; Go SQLite, migrations, recovery, failure tests, retention |
| ABH-06 Explorer, filesystem inspection & root search | RC-01/07; paged Go query/search and truthful scope/readiness |
| ABH-07 Home insights & visual analytics | RC-01/07/12; trusted summaries, scalable visual data, comparable history |
| ABH-08 Storage growth & history | RC-01/07/12; compatibility groups and paged history |
| ABH-09 Large-file workbench | RC-02/03/07/08; size truth, proof-preserving actions, paging/package boundaries |
| ABH-10 Duplicate detection & verification | RC-02/03/05/06/09; Go verification, retained-copy proof, operation ownership |
| ABH-11 App residue detection | RC-03/08/09; current package/user evidence and explicit ambiguity |
| ABH-12 App storage inspector & package intelligence | RC-03/07/08/09; normalized package evidence and scalable queries |
| ABH-13 Cleanup recommendations & rules | RC-03/07/09/13; proof-bearing output, limits/conflicts, behavioral tests |
| ABH-14 Destructive cleanup safety/execution/verification | RC-04/05/06; immutable review/capability, mutation FSM, recovery/verification |
| ABH-15 Scheduling, Tasker & notifications | RC-10/11; common operation truth, terminal-result semantics, stale action guards |
| ABH-16 Navigation/shared state/diagnostics/final integration | RC-01/06/11/14; stable routes, shared gates, diagnostics/evidence/privacy |

No original ABH section may be closed merely because its legacy implementation is removed. Closure needs either corrected replacement behavior or proof that the obsolete path is gone and its user-level contract is covered by the replacement.

## 15.2 Concrete DustUI findings discovered after the original bug hunt

These are tracked findings, not optional notes.

### N-01 — Devtool release variant is defeated by hard-coded Debug tasks

Current configuration pins `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, plus Debug-specific compile/resources/package validation phases such as `compileDebugSources` and `processDebugResources`. A Devtool `release` plan can therefore remain a Debug build/test/lint/package plan.

**Required correction:** semantic variant-aware tasks/workflows (`assemble`, `unit-test`, `lint`, instrumentation) so Release resolves to Release tasks.

### N-02 — Devtool workspace identity/orchestration is stale and under-modeled

Verified issues include:

- no `[workspace].default_target` despite multiple targets;
- legacy `kind`/`type` configuration identity;
- stale/misleading `ubuntu_provisioner` target although the Python project is `dust-tui` / `dust_tui`;
- custom task names under that stale Python target do not describe the current runner behavior reliably;
- no explicit Android input/output scopes, so EXO cannot safely reason about writes;
- no meaningful Dust workflow/job graph, so EXO sees a coarse fallback runner instead of compile/test/package/domain/release nodes;
- hard-pinned `aapt2_provider = "termux"` rather than current provider resolution unless a measured exception requires the pin;
- Gradle structured events disabled;
- support scripts still invoke raw ADB and guess artifact paths instead of using Devtool Android operations/artifact registry.

**Preserve rather than discard:** the Termux resource tuning is useful: Gradle 9.7.1, two-worker limit, phased heap/metaspace/memory profiles, build cache, `split_gradle`, affected-module lint, artifact-required policy, and `verifyStableToolchainBaseline` should survive unless measurements justify changing them.

**`--affected` caveat:** a root-level Go/Python/reference target can overlap the nested Android tree. Do not restructure the repository merely to satisfy affected-file ownership unless the practical benefit warrants it; model jobs/scopes deliberately.

### N-03 — Several authoritative-file publishers fail open

Observed paths attempt atomic-ish temp publication and then fall back to direct overwrite/copy when rename fails, including cleanup plan/report/diagnostic/duplicate-related stores. Critical state must fail closed on failed atomic publication.

The Go migration should eliminate critical file authority where SQLite transactions are superior; remaining file publication must use fail-closed atomic publication with fsync/commit/failure-window tests.

### N-04 — Root service admission is unbounded

The current root service uses `Executors.newCachedThreadPool()` while safety-critical operation maps are process-local.

Bridge-period fix: bound privileged workers/queues and make destructive cleanup globally single-flight. Final fix: Go operation/mutation authority with explicit admission classes and durable state.

### N-05 — Debug health equates completion with health

The current Active Scan probe can report Pass from `session.status == COMPLETED` despite truncation, malformed events, observed/persisted divergence, degraded integrity, incompatible scope, or stale-after-cleanup state.

All health/readiness presentation must consume `ScanAuthority` plus evidence freshness; no screen/probe derives independent authority.

### N-06 — Cleanup publication/replay is weaker than available ecosystem contracts

`operationId` + serialized plan handoff is insufficient compared with BKX/chrootctl proof semantics.

Final capability binds at least:

```text
capabilityId
operationId
scanId + scanGeneration + scanContextHash
manifestHash + manifestRevision
rootIdentity
reviewedAt + expiresAt
single-use claim state
```

Execution never blindly replays after an ambiguous transport outcome; it queries/reconciles the admitted operation.

### N-07 — Current release/certification scripts duplicate generic Devtool authority

Hand-written Gradle/ADB/build/sign/install/smoke/evidence-presence logic must narrow to Dust-specific semantic gates. Devtool owns generic artifact, signing, certificate, device, install, smoke, and release identity/evidence, including Devtool/Git identity, resolved providers, signing policy and expected certificate, device identity, per-gate results, artifact SHA-256, install/smoke evidence, and final verdict.

### N-08 — Overlay evidence usage is inconsistent

The audited snapshot contains **118** Devtool artifact registry records, proving substantial historical integration, but the recorded `dustui_master_remediation_overlay_20260811.zip` entry has `status = ok`, `validation_status = not-run`, and an empty manifest.

Future migration overlays should use current manifest/checksum/inventory/target/validation contracts consistently. The final program must not infer that an overlay is qualified merely because it applied successfully.

## 15.3 Cross-project ecosystem findings retained by the audit

These are not DustUI product bugs, but they matter to the shared project ecosystem and must not disappear from the consolidated record.

### CP-01 — BKX snapshot is stale against canonical chrootctl module identity/path

The audited BKX resolver still uses `/data/adb/modules/chrootctl-magisk` as its active module directory, while the current chrootctl uses canonical ID/path `chrootctl` / `/data/adb/modules/chrootctl` and treats `chrootctl-magisk` as legacy. The otherwise-audited backend/protocol versions were compatible.

**Cross-project action:** fix BKX canonical resolution and add producer/consumer compatibility fixtures. Legacy path may remain diagnostic/migration evidence, not an active execution candidate.

### CP-02 — XBS and Flowspace duplicate a large Gradle/chroot helper

The audited `apps/xbs/android-app/gradle-build.py` and `apps/flowspace/gradle-build.py` are byte-for-byte identical (55,138 bytes; SHA-256 `a56427b0c78c14a466992c531c65ac333175cb5a58cb6dd9cdbedfc83775fa5a`).

**Cross-project action:** converge generic host/chroot/Gradle execution on Devtool, retaining only thin project-specific wrappers where needed.

---

# 16. Integrated migration roadmap

The UI remodel is merged into the Go program rather than added after it.

## GO00 — Freeze contracts, bug ledger, route inventory, Devtool convergence plan, and retirement map

Add:

- immutable ledger mapping every ABH/RC/N finding to a GO phase and closure test;
- migration contract/fixture/oracle bundle that freezes valid interfaces but explicitly records intentional semantic corrections;
- inventory of all current destinations and entry points;
- route-to-new-area mapping;
- screenshots/golden semantic fixtures for safety-critical states;
- accessibility labels and key task-flow fixtures;
- navigation/deep-link compatibility ledger;
- explicit list of old screens to retire instead of refactor;
- explicit retirement inventory for Python/C++/Kotlin domain implementations;
- rule that no new production engine/domain feature is added to Python/C++ during migration.

**GO00 safety gate:** every original ABH section, RC-01..RC-14, N-01..N-08, and relevant CP item is mapped before implementation advances.

## GO01 — Go module, typed errors, readiness, Devtool convergence and targets

Also define the UI-facing readiness and action-gate protocol.

Devtool requirements at this gate:

- canonical workspace with an explicit default target;
- remove/rename stale `ubuntu_provisioner` identity;
- canonical Go target plus Android target and only explicitly migration-scoped compatibility targets;
- semantic variant-aware Android tasks;
- explicit inputs/outputs and workflow/job graph so EXO has meaningful nodes and known write scopes;
- Gradle structured events enabled unless a measured blocker exists;
- provider selection returned to `auto` unless a documented Termux exception is required;
- current Termux resource profiles retained initially;
- Devtool Android operations replace raw ADB for generic device/install/smoke operations;
- Devtool artifact registry/export replaces guessed APK paths;
- explicit release/signing policy and Dust-specific qualification gates attached to canonical Devtool release evidence.

## GO02 — Go SQLite foundation

Persist operation, scan, evidence, history, and route-relevant entity identities required for reconstruction.

## GO03 — Operation runtime + UI protocol

Add:

- versioned readiness/execute/progress/cancel/status/reconcile envelopes;
- request fingerprint, sequence ordering, terminal-state stickiness, cancellation semantics, and bounded messages;
- durable operation state in Go SQLite;
- typed `OperationSummary` and `OperationDetail`;
- route/entity/generation response binding fields;
- shared loading/empty/blocked/error/content envelope;
- cancellation/reconciliation semantics.

## GO04 — Scanner replacement + size semantics

Expose explicit logical/allocated values and scan evidence labels for Overview/Files.

## GO05 — ScanAuthority + history compatibility

Expose:

- trusted/current/degraded/stale state;
- action gates;
- reason codes/remediation;
- history comparability explanations.

## GO06 — Paged query/search/summary engine

Build the APIs needed by Files, Large Files, Apps, Activity and Clean without loading entire scans into Compose state.

## GO07 — Android Go runtime integration + new shell foundation

This is the first visible remodel milestone.

Implement:

- stable typed route tree;
- adaptive navigation without route remapping;
- Overview/Files/Clean/Apps/Activity/System product areas;
- global Scan workflow entry;
- route restoration;
- deep-link translation;
- route-generation response binding;
- shared status/notice/action-gate components.

No destructive cutover yet.

## GO08 — Root Go runtime + privileged cutover

System → Runtime/Modules becomes the canonical UI for privileged readiness and scope diagnostics.

Required privileged-boundary properties:

- same Go domain engine as local execution, separate root namespace;
- protocol/version/request-ID validation in the privileged process;
- operation and field allowlists revalidated at the root boundary;
- explicit request/response/event byte ceilings (the BKX/chrootctl 256 KiB Binder request ceiling is the current donor precedent, not an immutable Dust constant);
- bounded worker/queue admission and globally single-flight destructive mutation;
- no arbitrary shell surface;
- durable operation lookup/reconciliation so lost Binder transport never causes blind destructive replay.

## GO09 — Cleanup evidence + immutable manifest/capability

Implement the redesigned Clean review flow end-to-end, but keep legacy executor behind a migration gate until GO10 is qualified.

## GO10 — Mutation engine + recovery

Wire Activity to durable cleanup progress, verification, interruption, and `Recovery required` states.

## GO11 — Android package/storage evidence model

Cut Apps over to the new authoritative package/storage model.

## GO12 — Duplicate/residue/recommendation/rules engines

Cut Clean's specialized subroutes over to Go and remove duplicated Kotlin domain logic as each route reaches parity.

## GO13 — Automation and run ownership

Implement System → Automation and make every scheduled/Tasker execution produce an Activity operation with terminal truth.

## GO14 — Diagnostics, evidence health, problem graph, retention and support evidence

Replace the old Debug Workbench's authority with System → Diagnostics:

- evidence generations with missing/stale/current/degraded semantics;
- root-cause problem graph/symptom suppression;
- structured persistent timeline correlated by operation ID;
- redaction before persistence/export, bounded retention, and pruning tied to scan/operation retention policy;
- guided fixes with explicit risk classes;
- sealed support-bundle manifest/checksums and explicit privacy modes;
- diagnostics remain explanatory evidence, never the source of operational truth.

## GO15 — Go CLI/TUI parity

Ensure the Go CLI uses the same operation/evidence semantics. Android UI remains a distinct presentation optimized for touch.

## GO16 — Android semantic/UI cutover

Make the new route hierarchy and Go authority the default product.

Requirements:

- no production navigation into retired legacy screens;
- no domain action uses Kotlin-local truth;
- no cleanup action bypasses the manifest capability;
- active/recent operations come from the Go coordinator;
- every retained top-level product area uses common screen-state/action-gate semantics; no acceptance gate depends on having exactly six areas;
- compact and wide layouts preserve route identity;
- old 16-destination shell is removed.

## GO17 — Retirement purge

Delete:

- C++ production scanner;
- legacy Python backend paths;
- retired Kotlin domain engines;
- old destination remapping/navigation model;
- legacy Debug/Certification/Deletion Reports top-level routes;
- stale source-contract tests that only assert implementation strings.

## GO18 — Adversarial qualification

Add behavioral UI qualification for:

- process death during scan/cleanup;
- route changes while async results are in flight;
- screen rotation/window resize without semantic route changes;
- stale scan after cleanup;
- wrong module scope;
- malformed/incomplete scan;
- transport loss after cleanup admission;
- duplicate cleanup capability replay;
- cleanup manifest mutation attempt;
- very large scan pagination/search;
- multi-user package/residue cases;
- accessibility traversal and screen-reader labels;
- destructive action confirmation and back-navigation behavior;
- deep links into a run/candidate that no longer exists;
- support export privacy modes.

## GO19 — Final migration + UX release seal

Final machine-readable audit must prove:

- production engine is Go-native;
- no C++ scanner production path;
- no independent Python production backend;
- no duplicated Kotlin domain authority;
- old 16-destination shell unreachable/removed;
- operation/activity truth survives recreation/process death;
- route generation rejects stale responses;
- cleanup review/execution proof is immutable and single-use;
- responsive layouts preserve route semantics;
- root/diagnostic evidence is explicit rather than silent-empty;
- Devtool release evidence passes and binds the exact Go runtime + APK/artifact identity;
- migration audit reports `runtime = go-native-android`, `python_runtime = none`, `cpp_scanner_runtime = none`, `android_ui = Kotlin/Compose`, `engine_database = Go-owned SQLite`, and no open migration promises;
- user-visible task flows pass instrumentation/usability contract tests.

---

# 16.1 Migration stop-ship and authority-transition rules

Until the relevant Go replacement is qualified:

- do not broaden destructive capability merely because a migration is underway;
- do not allow dual writers to authoritative scan/operation/cleanup state;
- do not let old and new engines execute destructive operations concurrently;
- do not silently fall back to legacy cleanup after Go rejects/admitted-then-loses a request;
- do not freeze known-bad behavior into migration contracts;
- do not claim parity from source/token tests alone;
- do not retire an old path until its replacement has behavioral and failure-window evidence;
- do not keep Python/C++ as a hidden production fallback after the retirement gate;
- if a migration flag exists during development, each operation has exactly one authority.

Temporary pre-cutover safety patches are still mandatory when legacy behavior remains reachable: reject degraded/truncated scans for destructive actions, constrain recursive cleanup where review/execution equivalence is unproven, prevent blind replay after ambiguous transport loss, fail closed on critical atomic-publication failure, bound privileged admission, correct the Release-vs-Debug Devtool defect, and prevent automation from reporting launch/admission as success.

# 16.2 Closure and obsolescence rules

The original bug hunt remains the acceptance/adversarial-test ledger. A finding may close only by:

1. **Corrected in Go** — the replacement behavior is implemented and exercised;
2. **Structurally retired** — the unsafe implementation path is unreachable/removed and equivalent user behavior is covered by replacement tests;
3. **Externalized to Devtool** — only the generic build/device/sign/release portion moves out, and Dust-specific semantic gates consume the same exact Devtool run/artifact identity.

Implementation-specific symptom classes expected to disappear after verified cutover include C++ scanner/native divergence, Kotlin NDJSON-tail lifecycle races, process-local operation maps as authority, mutable cleanup-cache authorization, Python-vs-Android engine drift, final Room-vs-Go domain duplication, and Kotlin duplicate/residue/rule business-policy duplication. Their semantic safety requirements do **not** disappear.

# 16.3 Donor-system details that are requirements, not inspiration-only

### BKX

Adapt into Go/Android contracts:

- operation states equivalent to queued/running/cancelling/success/failure/cancelled/interrupted/unknown;
- request identity/fingerprint, monotonic event sequence, late-event rejection, terminal-state stickiness, reconnect/reconcile semantics;
- preview/capability proof fields such as ID + payload/manifest hash + resource revision/generation + expiry;
- privileged protocol/version/request/field allowlists and bounded messages;
- BKX's audited privileged pool (2 workers / queue 4) is a useful bounded-admission precedent, not a required Dust constant;
- Tasker/automation rule that accepted/queued/running is not terminal success;
- centralized action gate with reason code/message.

Do not copy BKX's storage choice blindly; final Dust operation authority is Go SQLite.

### chrootctl

Adapt:

- universal destructive mutation boundary;
- process-shared/global serialization semantics;
- single-use claim before destructive execution;
- mutation states including interrupted/ambiguous/`repair_required` and cleanup-debt concepts where applicable;
- process/runtime reconciliation and no replay-on-lost-response;
- operation-journal evidence fields useful for recovery (operation/parent IDs, phase, timestamps, process/boot identity where meaningful, errors/warnings/repair flags);
- fail-closed atomic publication and sealed/checksummed support evidence.

Do **not** add chrootctl as a runtime dependency merely to reuse these concepts.

### XDM

Adapt:

- expected-generation state transitions;
- Android `AtomicFile`/fail-closed publication discipline where files remain;
- failure injection between write and commit;
- structured persistent diagnostics with redaction-before-persistence, bounded values/files/sessions and no automatic upload;
- separately persisted significant-problem records;
- operation/activity correlation and last-intent/stale-response protection;
- typed release/domain gate ownership.

### XBS

Adapt:

- evidence generation invalidation;
- missing/stale/current/degraded health policy;
- root-cause problem graph rather than duplicate symptoms;
- guided-fix risk classes;
- nonce/expiry/replay guards for stale user actions/notifications;
- security seal concepts for exported components, backup/cleartext/managed-path/external-entry/stale-action constraints;
- readiness based on fresh evidence rather than evidence-file presence;
- searchable/filterable Activity/recovery UX.

### Devtool

Retain as external authority for toolchain, workflows/EXO, Android device operations, artifact/signing/install/release evidence, overlay transactions, and artifact registry. Dust's Go engine must not recreate these concerns.

---

# 17. UX validation matrix

Every release gate should exercise these user outcomes, not merely Compose source structure.

| User task | Required proof |
|---|---|
| Understand current storage state | Overview shows trustworthy scan identity, size semantics, freshness, warnings, and useful next action |
| Find a file/folder | Files explorer works with pagination and stable navigation |
| Search entire scan | Whole-scan search is clearly distinguished from local filtering |
| Find largest consumers | Large Files and visual storage routes operate on paged Go data |
| Find cleanup opportunities | Clean explains reason/confidence and never equates recommendation with permission |
| Remove files safely | exact immutable review → authorization → execution → verification |
| Handle duplicate files | verification/confidence and keep/delete choices are explicit |
| Inspect residue | package/user/evidence ambiguity remains visible |
| Inspect an app | Apps shows supported/unsupported/partial storage evidence truthfully |
| Monitor a long operation | Activity preserves run state across navigation/process recreation |
| Recover interrupted work | Activity exposes reconcile/repair-required state rather than replaying blindly |
| Diagnose root/runtime | System provides root cause and remediation, not silent empty content |
| Export support evidence | privacy mode and included evidence are explicit; no file contents leak |
| Use tablet layout | same route/task meaning, denser master-detail presentation, no destination substitution |

---

# 18. Visual/product acceptance rules

1. Storage information can be dense; navigation should not be.
2. A screen should have one dominant user question and at most one dominant primary action.
3. Destructive primary actions appear only in dedicated review/confirmation contexts.
4. Problems that invalidate data are shown near the affected data, not only in Diagnostics.
5. Technical IDs/paths remain copyable/selectable where useful but are secondary to plain-language status.
6. Empty, unsupported, blocked, stale, and failed are visually and semantically distinct.
7. Phone and tablet show the same conceptual product, not two different navigation models.
8. Charts always have textual/accessible equivalents and exact values available.
9. File/path lists optimize for scanning and comparison, not oversized decorative cards.
10. Advanced/root/debug detail is available without making the normal product feel like a developer console.

---

# 19. Cross-project donor responsibilities in the final design

## BKX

Primary donor for:

- task-first navigation;
- nested route discipline;
- route generation / stale-response binding;
- readiness surfaces;
- activity/run presentation;
- progressive review-before-mutation flows;
- consistent cards/status treatment;
- grouped advanced settings/tools.

## chrootctl

Primary donor for:

- mutation/recovery semantics;
- single-use destructive capability behavior;
- process-shared serialization;
- ambiguous outcome / `repair_required` handling;
- support-evidence packaging discipline.

## XDM

Primary donor for:

- generation-safe persistence transitions;
- structured persistent diagnostics;
- privacy/redaction;
- failure-injection testing;
- atomic publication discipline.

## XBS

Primary donor for:

- evidence freshness;
- health derivation;
- root-cause problem graph;
- guided remediation risk classes;
- readiness/problem presentation.

## Devtool

Authority for:

- build/toolchain/execution orchestration;
- Go + Android targets/workflows;
- EXO execution evidence;
- Android device operations;
- artifact/signing/release evidence.

---

# 19.1 Recheck completeness matrix

This recheck explicitly compared the current roadmap against all audit layers available in the handoff/runtime.

| Audit/source | Preserved in this document |
|---|---|
| Original 16-section Android bug hunt | Yes — ABH coverage table + exact RC-01..RC-14 meanings + GO closure rules |
| Original consolidated remediation | Yes — semantic requirements retained; one-off Kotlin/C++/Python fixes superseded where Go makes them redundant |
| Devtool audit | Yes — N-01/N-02/N-07/N-08, exact Release→Debug defect, stale target/workspace, EXO/workflow/write-scope gaps, provider/events/ADB/artifact issues, preserved Termux tuning |
| XDM donor audit | Yes — generation-safe persistence, diagnostics/redaction, atomic failure injection, problem/activity evidence, stale-response discipline |
| BKX donor audit | Yes — operation model, capability proof, root protocol bounds/allowlists, automation ownership, action gating, Go Android migration/runtime method |
| XBS donor audit | Yes — evidence freshness/invalidation, health/problem graph, guided fixes, security/readiness, Activity UX |
| chrootctl/support-plane audit | Yes — mutation/recovery/locking, single-use claim, reconciliation, support evidence, CP-01 compatibility issue |
| Cross-project support duplication | Yes — CP-02 retained |
| Full Go preference | Yes — Go engine + Go SQLite + Go local/root runtime; Kotlin only Android/presentation adapters; C++/Python/Kotlin domain authority retired |
| UI/UX preference | Yes — BKX-inspired behavior, Dust-specific product identity, **no fixed five- or six-destination requirement**; route/task semantics are the contract |

## 19.2 Known corrections made by this recheck

The previous Go+UI/UX audit was **not fully lossless**. This revision corrects these defects:

1. restored **RC-14 = privacy retention**, which had been incorrectly relabeled as atomicity/recovery;
2. restored concrete `N-01..N-08` findings instead of leaving only their architectural consequences;
3. restored `CP-01..CP-02` cross-project findings;
4. restored Go persistence/runtime specifics, migration-contract rules, and stop-ship/dual-authority constraints;
5. restored the exact Devtool convergence requirements and the useful Termux tuning that should be preserved;
6. made the proposed six-area UI grouping explicitly provisional so it cannot become an accidental acceptance criterion;
7. restored the original ABH section coverage matrix so no bug-hunt area can disappear behind architecture-level wording.

---

# 20. Final verdict

The strongest DustUI end state is **not** "BKX with storage screens" and not "the old DustUI with Go behind it."

It is a new DustUI where:

- the engine, state, operation model, cleanup safety, analytics and evidence are Go-native;
- Compose is a thin but high-quality Android product shell;
- navigation is organized around storage tasks rather than implementation modules;
- the current recommended grouping (Overview, Files, Clean, Apps, Activity, System) is only one coherent presentation of the required task domains;
- storage truth, file exploration, cleanup intelligence, package-owned storage, operation history, and runtime/diagnostic capabilities remain easy to discover even if UX testing changes the visible top-level count;
- Scan is a workflow available wherever required, not a permanent destination;
- specialized Dust capabilities remain deep and first-class without crowding the primary shell;
- tablet layouts increase information density rather than increasing navigation complexity;
- destructive actions all converge through one immutable review/capability/mutation path;
- every old bug-hunt safety requirement is either implemented in the new Go authority or structurally eliminated by deleting the legacy path.

The navigation count is therefore an implementation outcome, **not a design constraint**. Neither five nor six top-level destinations is required. The contract is discoverable Dust-specific task domains, stable semantic route identity, truthful operation/evidence state, and adaptive layouts that change density/chrome rather than changing what a route means.
