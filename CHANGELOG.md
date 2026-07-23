# DustUI changelog

## 1.4.0-rc1

Final release-candidate hardening for the rooted Android storage workbench.

### Android UI/UX safety overlay

- Bound cleanup review and execution to the same scan ID, root, operation ID, and accepted item set.
- Reset Explorer selection, details, filters, search results, package selection, and analysis when the active scan changes.
- Added hierarchical Android Back behavior for sheets, details, selection mode, Explorer folders, More workbenches, and the Scan root.
- Made storage charts, metric grids, package selection, and status pills adapt to compact screens.
- Added accessible treemap actions and 48dp interactive chart rows.
- Moved notification permission requests to scan and scheduling actions.
- Expanded cleanup review to list every accepted and blocked path before quarantine.
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
