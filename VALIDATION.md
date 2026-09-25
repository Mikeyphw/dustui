# Validation

Validated on Python 3.13.5 with Textual 6.12.0.

- `pytest -q`: **9 passed**
- `ruff check .`: **passed**
- Python bytecode compilation: **passed**
- Wheel build: **passed**
- Installed-wheel CLI smoke test: **passed**
- Installed-wheel Textual mount smoke test: **passed**
- Native scanner-to-DataTable integration test: **passed**


## Android cleaning intelligence final gate

From the repository root:

```bash
cd android
./scripts/cleaning-intelligence-gate.sh
```

The command runs `testDebugUnitTest` and `lintDebug`, audits the active cleaning surfaces and Room schema, and writes:

- `build/cleaning-intelligence/qualification.json`
- `build/cleaning-intelligence/QUALIFICATION.md`

`release-gate.sh` runs the same audit after the complete Android build, and `final-rc-gate.sh` treats missing or failed evidence as a release blocker.

## Android modern dark portrait final validation

Phase 8 is the final contract seal for the modern dark portrait UI roadmap. After applying the Phase 8 overlay, validate only the Android gates that prove the finished stack:

```bash
devtool \
  --copy \
  --auto-hud \
  --hud-mode desktop-window \
  --yes \
  -r "$HOME/Code/dustui" \
  --target dustui_android \
  apply-overlay "$HOME/Downloads/dustui_android_modern_ui_phase8_final_contract_overlay.zip" \
  --validate \
  --task :app:assembleDebug \
  --task :app:testDebugUnitTest \
  --task :app:lintDebug
```

The final accepted run must report zero failed tests, zero errors, and zero warnings. The bundled source audit `android/scripts/validate-modern-dark-portrait-ui.py` checks the sealed Home / Files / Clean / Tools navigation, compact scan sheet, Home dashboard, Files search split, Clean cockpit, Tools hub, and module-source diagnostics wording before Gradle validation runs.

## AUD-FIX-11 Activity / Diagnostics validation

AUD-FIX-11 is a non-gate stage. Apply with `--no-validate`, then run the Devtool-native stage workflow:

```sh
./devtoolw aud-fix-11
```

The workflow composes three separate Android-side leaf assertions (`activity_ui_contract`, `diagnostics_ui_contract`, `operation_evidence_navigation`), the Go durable Activity/Diagnostics backend leaf, Android unit tests, and canonical qualification freshness. Leaf validators do not launch Gradle or Go themselves.

Direct source acceptance may be inspected with:

```sh
python3 scripts/validate_audfix11_contract.py --section all
python3 android/scripts/validate-debug-workbench-contract.py .
```

The historical Debug Workbench is no longer an acceptance surface. Current acceptance requires user-comprehensible System → Diagnostics backed by the Go ProblemGraph, with raw probes/events behind Advanced disclosure, and cleanup verification attached to durable Activity evidence.

## AUD-FIX-07 Go host cutover

AUD-FIX-07 is intentionally a non-gate overlay. Apply it with `--no-validate`; do not turn it into a full validation gate.

Narrow static/qualification checks:

```bash
python3 scripts/validate_audfix07_contract.py --audit-loop
python3 scripts/refresh_qualification_state.py --check
python3 scripts/validate_ov13_contract.py
```

Go host checks are owned by the `dustui_go#aud_fix_07` Devtool workflow and cover CLI/JSON contracts, interactive TUI delegation, product identity, and a real Go host build. The federated `dustui_android#aud_fix_07` workflow adds static host reachability/product identity and prior Go regression coverage.

The normal full `validate` graph once again schedules `verifyDustMigrationContract`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`. Those Gradle jobs are **not** run merely by applying AUD-FIX-07 with `--no-validate`; their restored graph presence prevents the temporary Gate-R1 pin from becoming permanent validation drift.

### AUD-FIX-08 semantic navigation
Apply the non-gate overlay with `--no-validate`, then run the narrow native workflow:

```sh
./devtoolw aud-fix-08
```

The workflow runs the read-only semantic source contract, Go shell/product/TUI route tests, Android `:app:testDebugUnitTest`, and qualification freshness. Full `validate` remains reserved for later gates.

### AUD-FIX-09 bound async state and action gates
AUD-FIX-09 is a non-gate stage. Apply with `--no-validate`, then run:

```sh
./devtoolw aud-fix-09
```

The narrow workflow runs the read-only AUD-FIX-09 source contract, Go shell/product/TUI protocol tests, Android `:app:testDebugUnitTest`, and canonical qualification freshness. The static contract rejects MainViewModel `AtomicLong` request guards, predecessor no-scan presentation screens, enum-style action gates, and missing full-tuple stale-response coverage.

### AUD-FIX-10 Scan / Overview / Apps truth convergence
AUD-FIX-10 is a non-gate stage. Apply with `--no-validate`, then run:

```sh
./devtoolw aud-fix-10
```

The workflow runs the read-only AUD-FIX-10 source contract, the focused Go truth/gate suite, Android `:app:testDebugUnitTest`, and canonical qualification freshness. The source contract rejects any direct preset start bypass and requires exactly one production `repository.startScan(request)` execution point: the reviewed/confirmed path. Full `validate` remains a later gate.


## AUD-FIX-12 adaptive layout / route continuity validation

AUD-FIX-12 is a non-gate stage and is applied with `--no-validate`.

```sh
./devtoolw aud-fix-12
```

The stage workflow runs separate adaptive-layout, route-continuity, and accessibility/product-acceptance leaf contracts, Android unit tests, and qualification freshness. Connected-device continuity evidence is defined separately so the ordinary non-gate workflow does not silently depend on a device:

```sh
./devtoolw aud-fix-12-device
```

That device workflow runs `:app:connectedDebugAndroidTest`, including phone/tablet/rotation semantic identity and traversal-order assertions. Gate D remains open; later Gate-D topology replays connected route/deep-link/rotation and accessibility evidence on the qualified tree.

## AUD-FIX-13 acceptance authority

Run `./devtoolw aud-fix-13` for pre-Gate-D acceptance convergence. Its leaf jobs are `acceptance_inventory`, `successor_validator_contract`, `evidence_metadata_truth`, and `pre_gate_d_conformance`; runtime/build evidence is supplied by Devtool nodes rather than launched by those checkers. Gate D remains open and unqualified after this workflow. Historical OV/source-token validators live only under `migration/archive/validators/aud_fix_13_pre_gate_d/`.

## AUD-FIX-14 — Gate-D topology preflight

Run `./devtoolw aud-fix-14` to discover/prepare exact historical Gate A/B/C worktrees and validate Gate-D topology/binding contracts without executing Gate D. The complete qualification graph is exposed as `./devtoolw gate-d` for AUD-FIX-15; it remains blocked while historical proof or current-node evidence is incomplete. Debug and Release Android proof are separate nodes, and the Gradle 9.7.1 `jniLibs` source-set configuration blocker from the failed R1 attempt is repaired before Gate D.
