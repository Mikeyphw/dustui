#!/usr/bin/env python3
"""Historical OV01->OV13 readiness guard, successor-aware after AUD-FIX-07."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/migration/Ov01MigrationContractTest.kt"
CONTRACT = ROOT / "migration/contracts/v1/ov13_ov01_readiness_transition_hotfix.md"
AUDIT = ROOT / "migration/audit/v1/ov13_ov01_readiness_transition_hotfix_audit.md"
STATUS = ROOT / "migration/ledger/ov13_ov01_readiness_transition_hotfix_status.json"

failures = []
text = TEST.read_text()
for token in [
    "go host cutover is real while android reconnect remains gate d controlled",
    "ProductionEngine:\\\\s+true",
    "GoCLIDefault:\\\\s+true",
    "GoTUIDefault:\\\\s+true",
    "HostAuthorityDefault:          true",
    "ProductDefault: qualification.GateDQualified",
    "const GateDQualified = false",
    "const PythonTextualOraclePresent = true",
]:
    if token not in text:
        failures.append(f"missing successor-aware token in OV01 readiness transition test: {token}")
for path in [CONTRACT, AUDIT, STATUS]:
    if not path.is_file():
        failures.append(f"missing hotfix metadata: {path.relative_to(ROOT)}")
if failures:
    for failure in failures:
        print(f"OV13 OV01 readiness transition hotfix failure: {failure}")
    raise SystemExit(1)
print("OV13 OV01 readiness transition hotfix contract passed (AUD-FIX-07 successor-aware)")
