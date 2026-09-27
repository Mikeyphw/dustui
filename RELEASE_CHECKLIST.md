# DustUI release checklist — Gate E qualified

> AUD-FIX successor-aware. Current release authority is Devtool. Historical Room/app-local build, ADB, signing, install, and release scripts under `migration/archive/` are provenance only.

## Canonical qualification truth

- [ ] `migration/qualification/v1/current_state.json` reports Gate D `qualified` and preserves the AUD-FIX-15 ancestor binding.
- [ ] `ov14.status` is `physically-retired` and `migration/qualification/v1/ov14_retirement.json` is `pass` with `gate_d_resealed=false`.
- [ ] Gate E is `qualified`.
- [ ] `migration/qualification/v1/gate_e_evidence.json` and `gate_e_bundle.json` both report passing Gate-E evidence for the current qualified source projection.
- [ ] The Gate-E bundle reports zero open migration promises and zero new counterexample root families.

## Canonical release / requalification sequence

- [ ] Start from a clean committed source tree.
- [ ] Set the intended Android device (`ANDROID_SERIAL` when needed).
- [ ] Run `./devtoolw gate-e-release`; require the canonical signed release, tests, lint, package, instrumentation, install smoke, signature/certificate checks, and release evidence to pass.
- [ ] Do not modify source/configuration between the successful release run and final qualification.
- [ ] Run `./devtoolw aud-fix-17`; require every final regression/adversarial/device/runtime/migration/promise/convergence node to pass.
- [ ] Confirm `python3 scripts/validate_full_roadmap_conformance.py --scope all` reports zero blockers.

## Authority and retirement invariants

- [ ] Do not restore app-local generic ADB/install/release/signing orchestration such as the historical `device-certify.sh`, `install-smoke.sh`, `release-gate.sh`, or `final-rc-gate.sh`.
- [ ] Do not regenerate or rewrite Gate-D evidence for a post-Gate-D tree.
- [ ] The temporary `gate_e_fast` workflow and `aud-fix-17-fast` wrapper remain absent after Gate-E qualification.
- [ ] Rooted ARM64 and device instrumentation requirements are fail-closed; multi-user/work-profile evidence follows the Gate-E topology's conditional capability policy and may not be fabricated when unavailable.

## After qualification

- [ ] Commit intended derived Gate-E evidence without changing qualified source content.
- [ ] `git status --short` is clean.
- [ ] After later commits, run `./devtoolw gate-e-plan`; an unchanged source projection does **not** require qualification execution to repeat.
- [ ] For policy-only source changes with unchanged expensive/environment node fingerprints, run `./devtoolw gate-e-reconcile` and require policy revalidation + recomposed v2 evidence to pass.
- [ ] If reconciliation reports any changed expensive/environment node, treat the candidate as materially changed and run the canonical release/final qualification path required for that change.
