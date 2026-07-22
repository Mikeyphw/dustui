# DustUI final RC release checklist

## Local validation

- [ ] Apply the latest overlay with Devtool validation.
- [ ] Confirm unit tests pass.
- [ ] Confirm Android lint reports 0 warnings and 0 errors.
- [ ] Confirm debug APK builds.
- [ ] Confirm release APK builds.
- [ ] Confirm Room schemas are exported.

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
