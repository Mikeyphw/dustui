#!/usr/bin/env python3
"""M01 static contract check: read-only, stdlib-only, no Gradle or device."""
from __future__ import annotations
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PATHS = {
    "product": ROOT / "go/internal/product/product.go",
    "readiness": ROOT / "go/internal/readiness/readiness.go",
    "version": ROOT / "go/internal/version/version.go",
    "engine": ROOT / "go/internal/engine/engine.go",
    "tui": ROOT / "go/internal/tui/tui.go",
    "kotlin": ROOT / "android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductRuntimeContract.kt",
    "kotlin_test": ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/go/ProductRuntimeContractTest.kt",
}
OLD = (
    ROOT / "android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductCutoverContract.kt",
    ROOT / "android/app/src/test/kotlin/com/mikeyphw/dustui/go/ProductCutoverContractTest.kt",
)


def check() -> None:
    for name, path in PATHS.items():
        if not path.is_file() or path.is_symlink():
            raise ValueError(f"missing or symlinked {name}: {path.relative_to(ROOT)}")
    if any(p.exists() or p.is_symlink() for p in OLD):
        raise ValueError("old Android migration-contract files remain in the working tree")
    product = PATHS["product"].read_text()
    readiness = PATHS["readiness"].read_text()
    kotlin = PATHS["kotlin"].read_text()
    kotlin_test = PATHS["kotlin_test"].read_text()
    tui = PATHS["tui"].read_text()
    version = PATHS["version"].read_text()
    if 'Protocol = "dustui.product.v2"' not in product:
        # gofmt aligns variable spacing; accept spaces around the equals sign.
        if not re.search(r'\bProtocol\s*=\s*"dustui\.product\.v2"', product):
            raise ValueError("Go v2 product protocol missing")
    if not re.search(r'const\s+val\s+Protocol\s*:\s*String\s*=\s*"dustui\.product\.v2"', kotlin):
        raise ValueError("Android current product protocol missing")
    for name, text in {"product": product, "readiness": readiness, "tui": tui, "version": version, "kotlin": kotlin}.items():
        for old in ("OV13", "Gate D", "Gate E", "GateDQualified", "GateEQualified", "CandidateAcceptance", "MigrationPhase", "QualificationStateSource", "QualificationEvidenceSource", "PythonTextualOraclePresent", 'internal/qualification'):
            if old in text:
                raise ValueError(f"stale migration symbol {old!r} found in {name}")
    for field in ("json:\"phase\"", "json:\"gate\"", "json:\"migrationPhase\"", "json:\"gateDQualified\"", "json:\"gateEQualified\"", "json:\"legacyAndroidAuthority\"", "json:\"candidateAcceptanceStatus\"", "json:\"runtimePackaged\""):
        if field in product or field in readiness or field in tui:
            raise ValueError("obsolete/misleading runtime JSON field: " + field)
    if 'ProductCutoverContract' in kotlin or 'ProductCutoverContract' in kotlin_test:
        raise ValueError("Android still references old contract object")
    if 'ProductRuntimeContract.TargetGoRuntimeAuthority' not in kotlin_test:
        raise ValueError("Kotlin tests no longer exercise the new authority")
    print(json.dumps({"status":"PASS","stage":"M01","protocol":"dustui.product.v2","checks": ["source-identity","no-stale-product-migration-status","kotlin-product-contract","no-local-gradle"]}, sort_keys=True))


if __name__ == "__main__":
    try:
        check()
    except (ValueError, OSError) as exc:
        print(f"DUSTUI_M01_SOURCE_REFUSED: {exc}", file=sys.stderr)
        raise SystemExit(2)
