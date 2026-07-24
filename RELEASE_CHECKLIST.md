# DustUI final RC release checklist

## Local validation

- [ ] Apply the latest overlay with Devtool validation.
- [ ] Confirm unit tests pass.
- [ ] Confirm Android lint reports 0 warnings and 0 errors.
- [ ] Confirm debug APK builds.
- [ ] Confirm release APK builds.
- [ ] Confirm Room schemas are exported.

## Android deletion and UI/UX safety validation

- [ ] Start a cleanup review, replace the active scan, and confirm the review and selection are cleared.
- [ ] Confirm deletion review lists every approved path and every blocked path before the irreversible confirmation.
- [ ] Confirm Android Back closes review/details, exits selection, navigates Explorer upward, and returns compact More workbenches correctly.
- [ ] Confirm donut charts stack at 360dp and interactive chart rows remain at least 48dp.
- [ ] Confirm every treemap block has a keyboard/TalkBack-accessible matching action row.
- [ ] Confirm package search reaches packages beyond the former first-24 limit.
- [ ] Confirm notification permission is requested only when starting background scans or enabling scheduling.
- [ ] Confirm changing or replacing a reviewed file causes execution to refuse that path.
- [ ] Confirm selected symlinks, protected Android paths, scan roots, outside-root paths, and nested filesystem boundaries are blocked.
- [ ] Confirm a mixed plan deletes approved paths, skips blocked paths, and reports partial failures per item.
- [ ] Confirm actual deleted bytes are recorded and the UI requests a rescan after any successful mutation.
- [ ] Confirm old large files, search results, package paths, module source files, Explorer rows, treemap rows, and details sheets expose useful actions.
- [ ] Confirm module conflict actions target module-owned source files rather than protected system overlay targets.
- [ ] Confirm Kotlin and lint complete with 0 warnings, including no unnecessary safe-call warnings.

## Device validation

- [ ] Install the preferred APK on a rooted Android device.
- [ ] Run `android/scripts/install-smoke.sh`.
- [ ] Run `android/scripts/device-certify.sh`.
- [ ] Confirm the app launches without fatal exceptions.
- [ ] Confirm root certification has no failures.
- [ ] Confirm scanner, explorer, treemap, cleanup review, search, packages, modules, and certification screens open.

## Release evidence

- [ ] Run `android/scripts/release-gate.sh`.
- [ ] Run `android/scripts/final-rc-gate.sh` after device evidence is available.
- [ ] Save `android/build/release-candidate/dustui-*/manifest.json`.
- [ ] Save `android/build/release-candidate/dustui-*/SHA256SUMS`.
- [ ] Save `android/build/final-rc/dustui-*/SHIP_STATUS.md`.
- [ ] Attach device-certification archive.

## Ship rule

Ship only when `SHIP_STATUS.md` says `SHIP` or when every warning listed under `SHIP_WITH_WARNINGS` is intentionally accepted.
