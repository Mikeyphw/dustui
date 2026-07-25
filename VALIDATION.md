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
