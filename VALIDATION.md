# DustUI validation — post AUD-FIX-17

> AUD-FIX successor-aware. Devtool and canonical qualification evidence are the live validation authority.

Gate D is a preserved qualified ancestor, AUD-FIX-16 physical retirement is complete, and Gate E is qualified. Devtool is the live build/test/device/release authority; predecessor Python/Textual, Room/NDJSON, and app-local generic Android release/device procedures are historical only.

## Final release qualification

Run the two canonical source-bound phases from a clean committed tree:

```bash
cd "$HOME/Code/dustui"
export ANDROID_SERIAL=localhost:5555
./devtoolw gate-e-release
# Do not modify source/configuration after the release passes.
./devtoolw aud-fix-17
```

`gate-e-release` owns the signed Android release, unit/lint/package/device/install/signature/certificate evidence. `aud-fix-17` imports that canonical release report and executes the Gate-D regression set plus Gate-E Go adversarial/race coverage, Android Debug/Release checks, full connected instrumentation, rooted ARM64 runtime proof, runtime↔APK binding, migration/promise audits, counterexample convergence, finalization, post-seal audit, and qualification-state refresh.

A missing required environment leaves qualification open/deferred. Rooted ARM64 and device instrumentation are required. Multi-user/work-profile coverage is capability-conditional according to `gate_e_topology.json`: absence must be represented truthfully and must never be converted into manufactured evidence.

## Gate-E v2 reuse / reconcile

```bash
./devtoolw gate-e-plan
./devtoolw gate-e-reconcile
```

`gate-e-plan` is read-only. `gate-e-reconcile` is qualification-specific evidence composition, not an app-local generic task cache: Devtool remains the owner of generic execution/resume semantics. Reused nodes retain their original execution Git/run provenance and receive a current node-input fingerprint; policy nodes are executed on the successor projection.

## Narrow development checks

Use repository wrapper commands rather than raw permanent Gradle/ADB fallbacks:

```bash
./devtoolw unit
./devtoolw lint
./devtoolw build
./devtoolw go-fast
./devtoolw instrumentation
./devtoolw release-unit
./devtoolw release-lint
./devtoolw release-build
```

For final-retirement and qualification contracts:

```bash
python3 scripts/audfix16_retirement.py check
python3 scripts/validate_audfix17_contract.py --audit-loop
python3 scripts/validate_full_roadmap_conformance.py --scope all
```

## Evidence authority

Canonical current-state and final evidence live under `migration/qualification/v1/`:

- `current_state.json` — derived Gate-D / OV14 / Gate-E state
- `gate_d_evidence.json`, `gate_d_bundle.json` — immutable Gate-D ancestor proof
- `ov14_retirement.json` — post-Gate-D physical-retirement/source projection
- `gate_e_evidence.json`, `gate_e_bundle.json` — final Gate-E executed evidence and cross-bindings

These derived files are not independent declarations. Gate-E v2 binds qualification to `source_projection_sha256`; commit/tree IDs record provenance only. After a clean commit, use `./devtoolw gate-e-plan`. If the projection is unchanged, the existing seal remains valid without rerunning execution. If the projection changed only in policy/qualification surfaces and all expensive/environment node fingerprints are unchanged, `./devtoolw gate-e-reconcile` refreshes AUD-FIX-16 projection evidence, reruns the cheap policy leaves, and composes the unchanged execution evidence. If any expensive/environment fingerprint changed, reconciliation fails closed and canonical execution is required.

The temporary `gate_e_fast` / `aud-fix-17-fast` resume path is intentionally absent after Gate-E qualification. Historical validation procedures remain available only under `migration/archive/` for provenance and must not be used as live release authority.
