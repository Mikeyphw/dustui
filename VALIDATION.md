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

