#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/migration/Ov01MigrationContractTest.kt"
text = TEST.read_text()
required = [
    "generateSequence(File(\"\").canonicalFile) { it.parentFile }",
    "File(candidate, \".devtool.toml\").isFile",
    "File(candidate, \"android/app/src/main\").isDirectory",
    "File(candidate, \"migration/ledger/findings.json\").isFile",
    "private fun repoFile(path: String): File = File(repoRoot, path)",
]
missing = [item for item in required if item not in text]
if missing:
    for item in missing:
        print(f"OV13 OV01 repo-root hotfix failure: missing {item}")
    raise SystemExit(1)
print("OV13 OV01 repo-root hotfix contract OK")
