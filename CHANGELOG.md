# DustUI changelog

## 1.4.0-rc1

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
