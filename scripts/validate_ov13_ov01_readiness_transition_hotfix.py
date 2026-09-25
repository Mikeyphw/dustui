#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/migration/Ov01MigrationContractTest.kt"
CONTRACT = ROOT / "migration/contracts/v1/ov13_ov01_readiness_transition_hotfix.md"
AUDIT = ROOT / "migration/audit/v1/ov13_ov01_readiness_transition_hotfix_audit.md"
STATUS = ROOT / "migration/ledger/ov13_ov01_readiness_transition_hotfix_status.json"

failures = []
text = TEST.read_text()
for token in [
    "go readiness authority matches migration phase",
    "migration_phase = \\\"OV13\\\"",
    "GoCLIDefault",
    "ProductionEngine:",
    "SQLiteAuthority(Model)?:",
    "ReadAuthority:",
    "MutationAuthority:",
    "ProductionEngine:  false",
    "MutationAuthority: false",
]:
    if token not in text:
        failures.append(f"missing token in OV01 readiness transition test: {token}")

for path in [CONTRACT, AUDIT, STATUS]:
    if not path.is_file():
        failures.append(f"missing hotfix metadata: {path.relative_to(ROOT)}")

if "unsupported_devtool_jobs_workflows_added" in STATUS.read_text() and "false" not in STATUS.read_text():
    failures.append("hotfix status must not add unsupported Devtool jobs/workflows")

if failures:
    for failure in failures:
        print(f"OV13 OV01 readiness transition hotfix failure: {failure}")
    raise SystemExit(1)

print("OV13 OV01 readiness transition hotfix contract passed")
