# DustUI changelog

## 1.4.0-rc1

Final release-candidate hardening for the rooted Android storage workbench.

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
