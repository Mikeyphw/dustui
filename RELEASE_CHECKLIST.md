# DustUI release checklist — post AUD-FIX-16

> AUD-FIX successor-aware. Historical Room/app-local release procedures are archived under `migration/archive/` and are non-authoritative.

## Qualification

- [ ] `migration/qualification/v1/current_state.json` reports Gate D `qualified`.
- [ ] `ov14.status` is `physically-retired`.
- [ ] Gate E is `eligible-for-qualification` before AUD-FIX-17 runs.
- [ ] `migration/qualification/v1/ov14_retirement.json` is `pass` and `gate_d_resealed=false`.
- [ ] The retirement record preserves the same Gate-D ancestor source hash as `gate_d_bundle.json`.

## Build and semantic checks

- [ ] Run `./devtoolw aud-fix-16` after applying AUD-FIX-16.
- [ ] Android debug unit tests pass through the Devtool Android bridge.
- [ ] Android lint passes through Devtool.
- [ ] Android debug assembly passes through Devtool.
- [ ] Go fast successor tests/contracts pass.
- [ ] No live Room/KSP, NDJSON file-tail state transfer, Python/Textual oracle, or generic raw-adb/release helper authority remains.

## Device / release evidence

- [ ] Use Devtool workflows for Android build, instrumentation, artifact identity, and release assembly.
- [ ] Do not restore `device-certify.sh`, `install-smoke.sh`, `release-gate.sh`, `final-rc-gate.sh`, or equivalent app-local orchestration.
- [ ] Historical Gate-D evidence is immutable ancestor evidence; do not regenerate it for the AUD-FIX-16 tree.

## Final boundary

- [ ] Execute AUD-FIX-17 / Gate E only after this checklist and `./devtoolw aud-fix-16` are green.
