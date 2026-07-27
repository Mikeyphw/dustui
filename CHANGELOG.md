# DustUI changelog

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
