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

## Android debug workbench validation

For Phase 9–10 Debug Workbench changes, use targeted Android validation tasks when you do not need a full APK gate:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Before final debug roadmap sealing, include `:app:assembleDebug` and `:app:lintDebug` as well.

## Debug Workbench Phase 11 validation

Recommended quick gates after applying the Phase 11 diagnostic probes overlay:

```bash
devtool -r "$REPO" --target dustui_android validate continue \
  --task :app:compileDebugKotlin \
  --task :app:testDebugUnitTest
```

The source contract checks that diagnostic probes are reachable from Debug Workbench, are evidence-only, include `/data` and `/data/adb/modules` dry preflight targets, and preserve review-first deletion semantics.


## Debug Workbench Phase 12 validation

Phase 12 adds the export bundle layer for the Debug workbench. Validate with the targeted Android gates:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected source-contract coverage:

- `DebugExportMode.Safe`, `DebugExportMode.PathAware`, and `DebugExportMode.FullDiagnostic` exist.
- Safe export mode redacts `/data` and `/data/adb/modules` path samples.
- The Debug workbench exposes Export debug bundle, export mode chips, and a share action.
- The bundle writer emits JSON/JSONL entries for report, timeline, probes, scan, root, navigation, cleanup, recommendations, modules, and app info.

## Debug Workbench Phase 13 validation notes
- Compile and unit tests should include `DebugWorkbenchSourceContractTest`.
- Verify the Debug workbench exposes the UI debug overlay toggle.
- Verify the overlay is diagnostic-only and links back to Debug workbench.
- Verify disabled export/certification/scan/deletion actions include explicit reasons.

## Debug Workbench final validation

For Phase 14 and later Debug Workbench contract changes, run the source audit before Android gates:

```bash
python3 android/scripts/validate-debug-workbench-contract.py "$REPO"
```

Recommended Devtool gate:

```bash
devtool   --copy   --auto-hud   --hud-mode desktop-window   --yes   -r "$REPO"   --target dustui_android   apply-overlay "$HOME/Downloads/dustui_android_debug_workbench_phase14_final_contract_overlay.zip"   --validate   --task :app:compileDebugKotlin   --task :app:testDebugUnitTest   --task :app:lintDebug
```

Expected result: all selected tasks pass with zero Devtool diagnostics warnings and zero errors.


### Debug Workbench Phase 15

Validate Phase 15 with targeted Android gates:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-configuration-cache
```

Manual smoke check: launch a debug build, open Files/Clean/Tools before any scan, run a shared-storage scan, then confirm input remains responsive after completion and depth-cap evidence is informational unless another warning/failure is present.

- Phase 16 adds `ScanDepthProfile` source contracts, cooperative post-scan refresh checks, and Tasker depth bound checks.

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
