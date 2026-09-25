## AUD-FIX-12 — adaptive tablet/master-detail and route continuity

- Added compact/medium/expanded layout policy derived from semantic route identity.
- Added route-driven supporting/master panes for Files, Clean, Apps, Activity, and System without introducing layout-local destination state.
- Added explicit traversal ordering and shared textual equivalents for storage charts and treemap.
- Added phone/tablet previews, unit continuity tests, connected-device rotation/continuity tests, Devtool leaf contracts, and successor acceptance checks.
- Kept Gate D open; this is a non-gate remediation stage.

## Debug Workbench · Phase 14 final contract seal

### Debug Workbench Phase 15 scan freeze guard r3

- Realigned source-contract assertions with the Phase 15 scan-independent task-zone model so Files, Clean, Tools, Debug workbench, and deletion reports are verified through `DustNavigationModel.needsScanData(...)` instead of brittle contiguous source strings.


- Sealed the Debug Workbench roadmap after Phases 9 through 13 with final docs, source contracts, release checklist items, and a source-level audit script.
- Locked Debug workbench as a scan-independent Tools surface with Live state, Trace timeline, Diagnostic probes, Export debug bundle, and UI debug overlay sections.
- Locked the safety contract that probes, exports, overlays, and disabled-action explanations remain diagnostic-only and never bypass cleanup safeguards.
- Added final contract tests covering route behavior, bounded/redacted event storage, Safe export path policy, no-file-content export behavior, and the final source audit.


### Debug Workbench · Phase 12 export bundle

- Added Safe, Path-aware, and Full diagnostic debug export modes.
- Added a ZIP debug bundle writer with report, trace timeline, probe results, scan summary, root diagnostics, navigation state, cleanup state, recommendation summary, module diagnostics, and app info entries.
- Added Tools → Debug workbench export UI with mode chips, privacy copy, and last-export metrics.
- Preserved the diagnostic-only contract: exports never execute deletion, mutate scans, bypass stale-scan locks, or include file contents.
- Added source-contract coverage for export modes, redaction, bundle entries, ViewModel wiring, repository sharing, and UI copy.


## Android modern dark portrait UI — Phase 8 warning cleanup

- Cleans the final contract test helper by resolving `user.dir` with `requireNotNull` before constructing the project root `File`.
- Preserves the Phase 8 final seal behavior while removing the Kotlin Java-platform nullability warning from `ModernDarkPortraitFinalContractTest`.


### Android modern dark portrait UI phase 7 source contract hotfix

- Aligned the modern module diagnostics source contract with the root diagnostics contract-required wrong-scope wording so module scans continue to expose actionable `/data/adb/modules` guidance without failing the final unit-test gate.


### Android modern dark portrait UI phase 7 validation hotfix

- Restored the root-diagnostics contract phrase `Current scan does not include modules` in the modern module diagnostics title so wrong-scope module scans remain explicit and the existing source contract passes.
- Cleaned two Kotlin warning surfaces seen during Phase 7 validation: simplified device-certification root connection reporting and suppressed the intentional `ACTION_UNINSTALL_PACKAGE` compatibility intent use.


### Android modern dark portrait UI phase 7 compile hotfix

- Added the explicit `ExperimentalMaterial3Api` opt-in for the compact scan launcher bottom sheet so Phase 7 compiles cleanly under Kotlin/Compose warning-as-error builds.
- Added source-contract coverage to keep the opt-in attached to `ScanLauncherSheet`.

# DustUI changelog

## AUD-FIX-10 — Scan, Overview, and Apps UX truth convergence

- Replaced one-tap scan execution with a mandatory scope/preset → preflight → review → explicit Start protocol for presets, custom roots, targeted rescans, and module scans.
- Made Overview report Go-owned scan identity, generation, integrity/freshness, durable operation/activity truth, and distinct logical-versus-allocated byte semantics.
- Made Apps label scan-measured filesystem bytes separately from supported/partial/unavailable/blocked Android package evidence so unknown platform evidence can never look like zero.
- Added typed Go remediation gates for root unavailability, failed preflight, wrong scope, degraded integrity, stale/no-scan evidence, and unsupported evidence.
- Added the non-gate `aud_fix_10` Devtool workflow and successor audit/ledger/contract evidence; Gate D remains open.


## Android modern dark portrait UI phase 8 final contract seal

- Sealed the completed Home / Files / Clean / Tools compact portrait roadmap after Phases 1–7.
- Reconciled stale pre-revamp navigation contract wording so compact routes no longer claim five bottom destinations or primary returns to Scan.
- Corrected the remaining modern UI documentation to use the contract-required module diagnostic wording: `Current scan does not include modules`.
- Added final seal documentation and a source audit for the modern compact scan sheet, Home dashboard, Files search split, Clean cockpit, Tools hub, and module-source diagnostics.
- Added final source-contract tests and validation instructions for the minimal final Devtool gates: `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`.

## Android modern dark portrait UI phase 7

- Replaced the generic More list with a grouped Tools hub for Storage intelligence, Cleanup workbenches, and Operations.
- Kept existing advanced workbench routes intact while surfacing App storage, Root modules, Growth, Whole-scan search, Deletion reports, Device certification, and Scan diagnostics as task cards.
- Linked Scan diagnostics to the compact scan/evidence sheet and Whole-scan search to the internal Search route.
- Restyled Root modules with a module-source diagnostic summary that shows current root, required `/data/adb/modules` root, preflight state, source status, and focused module-scan action.
- Kept module overlay conflicts source-safe: protected mounted targets remain evidence, while actions apply only to module-owned files under `/data/adb/modules`.

## Android modern dark portrait UI phase 6

- Promoted Cleanup recommendations into a task-first Clean cockpit with recoverable-bytes hero, active/hidden/safe-batch/selected metrics, and explicit stale-data locking.
- Added cockpit chips that keep Recommended cleanup in the Clean route while opening Duplicates, Large files, App residue, and Rules in their specialized workbenches.
- Restyled recommendation cards with safe-batch/manual-review badges, flat metric tiles, and clearer review-first deletion actions.
- Restyled exact-path review sheets with bottom-sheet headers, permanent-deletion safety evidence, Files reveal, selection, and Review actions.
- Preserved scan-bound, fingerprinted, permanent-deletion semantics and disabled destructive planning while scan data is stale.


## Android modern dark portrait UI phase 2

- Changed compact portrait navigation to the task shell: Home, Files, Clean, and Tools.
- Kept the Scan route available internally while selecting the Home tab in compact mode; Home opens the scan route until scan data exists.
- Mapped whole-scan search to the Files tab and advanced workbenches to Tools.
- Promoted Cleanup recommendations to a primary compact tab.
- Renamed the More hub to Tools while preserving all advanced destinations.
- Fixed the Cleanup Reports date-window Compose lint warning by using `mutableIntStateOf`.


## 1.4.0-rc1


### Android modern dark portrait scan sheet

- Added Phase 3 compact scan launcher: portrait mode now opens scans from a persistent top-bar Scan action and modal bottom sheet instead of using Scan as a compact bottom-tab path.
- Kept the full Scan route available for wide layouts and compatibility.
- Added a Home no-scan state with a Start scan CTA instead of silently routing Home to Scan.
- Surfaced root identity, scanner backend, SELinux state, fatal preflight failures, latest zero-file/truncation/depth diagnostics, and `/data/adb/modules` module-source scanning inside the scan sheet.
- Updated navigation/source contracts for the scan-sheet behavior.

### Modern dark portrait UX · Phase 1 design primitives

- Added dark-first Material 3 color tokens, rounded flat shapes, and a lime/cyan/amber/error state vocabulary for the upcoming portrait shell revamp.
- Added reusable modern Compose primitives for hero cards, task cards, evidence notices, metric tiles, action docks, flat icon buttons, bottom-sheet headers, scrollable filter chips, and path pills.
- Added UI source-contract coverage so later navigation, scan-sheet, Home, Files, Clean, and Tools overlays reuse shared components rather than inventing one-off surfaces.

### Phase 24 · Cleaning intelligence final gate

- Added a cleaning-specific qualification model that blocks final RC readiness when safety, migration, recovery, accessibility, performance, or product contracts are not proven.
- Added consolidated regressions for scan-bound reviews, protected paths, fingerprints, hard links, multi-user residue, imported rules, historical snapshots, interrupted deletion, and authoritative verification.
- Added Room schema and non-destructive migration contracts, 10,000-file scale coverage, accessibility/source parity checks, and process-recovery contracts.
- Added `cleaning-intelligence-gate.sh` and machine-readable qualification evidence collected from JUnit XML, lint SARIF, Room schemas, and source audits.
- Made release packaging and the final-RC gate require passing cleaning qualification evidence, with a zero-warning and zero-active-quarantine-runtime policy.


### Phase 23 · App storage inspector

- Replaced the shallow Packages summary with a package-centered storage inspector covering APK/split code, private data, databases, preferences, cache, OBB, shared media, logs, exports/backups, verified duplicates, residue, and recent growth.
- Switched package totals to leaf-file accounting so directory rows cannot double-count measured bytes.
- Blocked direct deletion of active app code, databases, preferences, and private state; active cache routes to Android app settings.
- Added exact bucket files, Explorer reveal, protection controls, selection, and fingerprinted deletion planning for eligible paths.
- Added Android-owned app settings and uninstall flows, package-specific recommendation ignore/restore, search, sorting, paging, and stale-scan guards.
- Added focused model and source-contract coverage with a zero-warning validation gate.


### Phases 20–22 · Complete rules, history, and verified reports

- Added automatically refreshed cleanup-rule draft previews with exact matched/blocked paths and complete condition explanations.
- Enforced explicit review before imported rules can be enabled, relative-depth matching, bounded safe path expressions, and schema migration tests.
- Expanded storage history to arbitrary endpoints, six grouping modes, a multi-scan timeline with accessible controls, specialized growth insights, targeted rescans, and configurable snapshot retention.
- Made every comparison not ending at the newest active scan strictly informational so historical snapshots cannot masquerade as live deletion targets.
- Expanded deletion reports with attempted/partial/failed accounting, device/filesystem context, persistence diagnostics, retryable verification, root/date/status filters, migration coverage, and smallest-safe-parent rescans.
- Kept rules, history, and reports behind the existing scan-bound fingerprinted permanent-deletion planner, with zero automatic deletion.


### Phase 19 · Ranked cleanup recommendations

- Added an explainable cleanup queue combining verified duplicates, high-confidence app residue, large old files, stale installers, old archives, and oversized logs.
- Added deterministic ranking with visible size, confidence, safety, and age point breakdowns.
- Limited page-level batch actions to active verified or high-confidence recommendations; heuristic age/size candidates require explicit review.
- Added exact-path sheets with Details, Explorer, selection, and permanent-deletion planning for every surfaced path.
- Added reversible Dismiss-until-next-scan, persistent Ignore, Show hidden, and Restore controls.
- De-duplicated overlapping recommendation paths and preserved duplicate SHA-256 expectations through deletion review.
- Kept recommendation-screen padding local to its source file so the Compose screen does not depend on a file-private helper.

### Phase 18 · Uninstalled-app residue detection

- Added a dedicated app-residue workbench with evidence-ranked package groups, confidence filtering, deterministic paging, and exact-path actions.
- Added fixed root-service queries for Android users and installed packages so high confidence requires package absence across every discovered user.
- Classified managed Android package directories separately from probable exports and uncertain name-only shared folders.
- Downgraded candidates when package visibility is incomplete or related installed packages may still own the data.
- Added persistent user-protected residue folders that are excluded from recommendations and batch selection.
- Kept probable and uncertain locations unselected by default while preserving explicit details, Explorer reveal, selection, and reviewed permanent-deletion planning.
- Added legacy `/data/data` alias de-duplication, focused analyzer tests, navigation contracts, and release validation.
- Switched residue protection persistence to the AndroidX KTX `SharedPreferences.edit` extension so lint remains warning- and error-free.

### Phase 17 · Exact duplicate detection

- Added a dedicated Android duplicate workbench backed by complete same-size candidate buckets from the active scan.
- Added edge hashing followed by full SHA-256 verification before any files are declared exact duplicates.
- Collapsed paths by device and inode so hard links never inflate reclaimable-byte estimates or become automatic deletion candidates.
- Added resumable, cancelable, scan-bound analysis persisted after each completed size bucket.
- Added newest, oldest, shortest-path, preferred-folder, and manual retained-copy strategies with deterministic paging.
- Bound expected SHA-256 values into duplicate deletion plans and reverified content during review and immediately before permanent deletion.
- Excluded protected paths from recommended deletion while keeping them visible as retained-copy anchors.
- Added group/page selection, exact-path inspection, Explorer reveal, and reviewed permanent-deletion planning.

### Phase 16 · Large file workbench
- Phase 16 r3 aligns the scan-sheet source contract with the quieter depth-boundary wording introduced by the adjustable depth policy.

- Added a dedicated Android large-file destination backed by the complete active-scan entry set rather than only the Explorer display window.
- Added presets for 100 MiB+, 500 MiB+, 1 GiB+, older files, old archives, large videos, and APK installers.
- Added name/path/package search, category filtering, deterministic sort modes, page-local grouping, and 40-item paging.
- Made every workbench row actionable through details, Explorer reveal, selection, and the existing fingerprinted permanent-deletion planner.
- Added page-level Select page and Plan page actions with stale-scan blocking after filesystem mutation.
- Added focused model, navigation, and UI source-contract coverage.

Final release-candidate hardening for the rooted Android storage workbench.

### Actionable storage doughnut overlay

- Made Overview storage-composition doughnut segments directly open the exact indexed files represented by the tapped slice.
- Added selected-slice center details for category, bytes, percentage, and file count.
- Added accessible category rows plus View files, Select all, and Plan deletion actions routed through the existing reviewed permanent-deletion pipeline.
- Made the aggregate Other slice retain its exact source categories and expose a detailed category list without duplicate Other labels.
- Added a category file sheet where every file supports selection, details, Explorer reveal, and permanent-deletion planning.
- Disabled category selection and deletion actions when scan results are stale after filesystem mutation while keeping read-only inspection available.

### Permanent deletion and actionable-path overlay

- Replaced the Android quarantine/restore workflow with review-bound permanent deletion.
- Added exact per-path planning with protected-path checks, scan metadata validation, filesystem-boundary rejection, symlink rejection, and device/inode/tree fingerprints.
- Revalidated every approved fingerprint immediately before mutation and reported each top-level item as deleted, partial, or failed.
- Recorded actual deleted regular-file bytes and marked scan results stale after any successful filesystem mutation.
- Made old large files, root-search results, package paths, module-owned files, module conflict sources, Explorer entries, treemap entries, and detail sheets actionable through selection, inspection, reveal, and deletion planning.
- Kept protected overlay target paths informational while exposing the real module source files that can be reviewed.
- Removed the four unnecessary safe-call warnings from module analytics and storage-chart callbacks.

### Android UI/UX safety overlay

- Bound cleanup review and execution to the same scan ID, root, operation ID, and accepted item set.
- Reset Explorer selection, details, filters, search results, package selection, and analysis when the active scan changes.
- Added hierarchical Android Back behavior for sheets, details, selection mode, Explorer folders, More workbenches, and the Scan root.
- Made storage charts, metric grids, package selection, and status pills adapt to compact screens.
- Added accessible treemap actions and 48dp interactive chart rows.
- Moved notification permission requests to scan and scheduling actions.
- Expanded cleanup review to list every accepted and blocked path before permanent deletion.
- Explicitly guarded scan-dependent Search and Packages routes so nullable session state compiles safely under Kotlin smart-cast rules.

### Added

- Final RC readiness model with ship, ship-with-warnings, and do-not-ship decisions.
- `scripts/final-rc-gate.sh` for release-gate execution, RC bundle checks, known-issues review, and ship-status evidence.
- Final RC evidence output under `android/build/final-rc/`.
- Release checklist, known-issues ledger, and final RC documentation.

### Validation baseline

- Phase 14 baseline before this overlay: 59 tests, 0 warnings, 0 errors.
- Phase 15 adds release-readiness and final-gate contract tests.

## 1.3.0-rc1

- Added RC packaging, install/launch smoke evidence, APK checksums, APK classification, and device-certification archive support.

## 1.2.2-rc1

- Completed compact UI/UX and typography polish, including readable old-file cards, metric grids, explicit List/Treemap controls, and navigation cleanup.

## 1.1.0-rc1

- Added package intelligence, indexed root file search, and root module analysis.

## 1.0.0-rc1

- Added root explorer metadata, bounded previews, and treemap visualization.

## Android modern dark portrait UI — Phase 4 home dashboard

- Converts the compact Home route into a task-first Storage health dashboard.
- Keeps Home available before the first scan with a large Start scan CTA instead of bouncing users to another tab.
- Surfaces scan diagnostics, zero-file warnings, stale-scan state, and cleanup locks directly on Home.
- Adds best-next-action cards for recommended cleanup, duplicates, large files, and app residue while preserving the existing workbenches.
- Keeps storage composition, snapshot history, file age, and actionable large-old-file cards reachable from the Home flow.

## Android modern dark portrait UI — Phase 5 files and search

- Modernizes the Files task zone with a flat search card, explicit This folder versus Whole-scan search copy, type chips, and cleaner route-preserving controls.
- Keeps whole-scan search as the internal Search route while compact navigation continues to highlight Files.
- Restyles file rows around larger touch targets, clear metadata, path visibility, details, and selection.
- Moves Explorer selection mode onto the shared `DustActionDock` while preserving review-first, fingerprinted deletion.
- Adds whole-scan search examples and clearer result copy that reveals matches back into Files.

### Android debug workbench · Phase 9–10

- Added a bounded structured debug event recorder for app, navigation, scan, root, search, deletion, and certification flows.
- Added Tools → Debug workbench with live state, category-filtered timeline, warning/error filter, and clearable in-memory diagnostics.
- Added source-contract and store coverage for scan-independent Debug Workbench reachability, redacted evidence, and route-safe diagnostics.

### Android debug workbench · Phase 11 diagnostic probes

- Added evidence-only diagnostic probes for root service, `/data`, `/data/adb/modules`, active scan state, zero-file scans, module scope, deletion readiness, and whole-scan search readiness.
- Added probe result cards inside Debug Workbench with Run/Rerun controls and a Run all probes action.
- Recorded probe start and finish events into the structured debug timeline with redacted evidence.
- Added source-contract coverage to keep probes non-destructive and review-first cleanup intact.


### Debug Workbench · Phase 13 UI overlay and disabled-action explanations
- Added an opt-in floating UI debug overlay from Debug workbench with route/layout/scan/root/stale/selection/module/event state.
- Added explicit disabled-action explanations for scan, export, certification, stale deletion, and permanent-deletion confirmation gates.
- Kept overlay and explanations diagnostic-only with no cleanup safety changes.

### Android Debug Workbench · Phase 15 scan freeze guard

- Moved post-scan storage intelligence refresh off the UI thread to reduce ANR risk after scan completion.
- Shifted expensive derived workbench paging/filtering flows to background contexts.
- Kept Files, Clean, Tools, Debug workbench, and deletion reports reachable before the first scan with explicit no-scan explanations.
- Raised default scan traversal depth to 16 for custom and built-in Android scopes.
- Reclassified depth-cap UI copy as informational unless truncation, zero-file, warning, or failure evidence is present.

### DustUI Android Debug Workbench · Phase 16 performance/depth controls

- Added adjustable scan-depth policy controls for Fast, Forgiving/default, and Deep scans.
- Made Forgiving the default depth behavior with unlimited traversal depth plus an entry cap to protect the UI.
- Raised allowed depth ceiling to 128 for automation/manual scan requests while preserving `-1` unlimited depth support.
- Added cooperative checkpoints inside post-scan insight refresh to reduce long CPU bursts after scan completion.
- Reworded depth-cap evidence as quiet informational boundary evidence unless paired with zero-file, truncation, warning, or error signals.

## AUD-FIX-07 — Go host cutover

- Added real Go CLI workflows for persisted scan/query/search, activity, cleanup review/execution, diagnostics, support export, and contract inspection.
- Replaced the Go TUI Render/Smoke-only scaffold with an interactive loop that delegates data-bearing work to the same Go host executor as the CLI.
- Moved the Python/Textual host to `migration/oracle/python_textual/` as a non-production parity oracle and cut `dust-tui`/`dust-space` to Go compatibility binaries.
- Separated Go host-default truth from Android Gate-D qualification; Gate D remains open and Android remains evidence-gated.
- Restored Gradle jobs to the normal full validation DAG after the temporary Gate-R1 deferral ended.

## AUD-FIX-08 — semantic navigation cutover
- Replaced the legacy 16-destination/layout-remap shell with a 27-route semantic contract shared by Android and Go.
- Added nested/entity `dustui://` deep links, saveable route/entity restoration, and typed stale/not-found identity handling.
- Decomposed System into runtime, modules, automation, diagnostics, certification, and settings; durable runs/evidence now live under Activity.
- Added Devtool-native AUD-FIX-08 static, Go route, and Android unit validation while keeping Gate D open.

## AUD-FIX-09 — bound async state and typed action gates
- Replaced scattered MainViewModel request counters with a semantic route request registry bound by route, generation, entity, request ID, and optional fingerprint.
- Routed production content presentation through shared Loading / Empty / Blocked / Error / Content states backed by the Go screen-state protocol.
- Retired the Files/Clean/scan-required one-off predecessor presentation screens.
- Centralized scan cancellation, cleanup review/execution, and Activity cancel/retry/reconcile policy in structured Go action gates with stable reason codes and remediation actions.
- Added adversarial stale-response coverage for directory queries, package switches, cleanup manifests, Activity selection, diagnostics generations, request IDs, and fingerprints.


## AUD-FIX-11 — Activity and Diagnostics product UX convergence
- Made durable Go Activity runs the canonical operation list/run/evidence UX, including filters and recovery-aware typed action gates.
- Attached full cleanup deletion verification to Activity evidence and retired the separate cleanup-report screen.
- Replaced primary Debug Workbench acceptance with ProblemGraph-backed System → Diagnostics; raw events/probes remain Advanced-only.
- Added exact bidirectional operation correlation between Activity evidence and Diagnostics remediation.
- Added Devtool `aud_fix_11` Android/Go DAG leaves and successor behavioral acceptance.
## AUD-FIX-13 — pre-Gate-D acceptance convergence
- Replaced stale source-token acceptance with an explicit validator lifecycle map and exact behavioral successor evidence.
- Archived predecessor Android contract tests and Python validators as non-authoritative history while preserving the live static guards Gate D still needs.
- Added evidence-derived candidate acceptance state, a complete UI/UX requirement/task/visual-rule matrix, and adversarial counterexample validation.
- Bound Android/Go product metadata and Devtool acceptance workflows to canonical acceptance + qualification state without claiming Gate-D qualification.
- Kept app-local ADB/APK/release helpers quarantined for AUD-FIX-16 and historical Gate A/B/C evidence/topology work for AUD-FIX-14.


## AUD-FIX-14

- Added exact-tree historical Gate A/B/C discovery/requalification contracts and worktree preparation.
- Declared the complete 26-node Gate-D Devtool topology with exact binding requirements.
- Added Debug/Release Android, device/runtime, runtime/APK, Devtool identity, durable-operation, diagnostics, and production-reachability leaves.
- Preserved the failed AUD-FIX-05/06 R1 result and repaired its known Gradle 9.7.1 SourceSet blocker without retroactively marking R1 passed.
