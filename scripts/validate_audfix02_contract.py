#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(errors="replace") if path.exists() else ""


def exists(rel: str) -> bool:
    return (ROOT / rel).exists()


def has_any(text: str, tokens: list[str]) -> bool:
    return any(token in text for token in tokens)


def main() -> int:
    main_vm = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    repo = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    db_contract = read("android/app/src/test/kotlin/com/mikeyphw/dustui/data/DatabaseSchemaContractTest.kt")
    go_contract = read("android/app/src/test/kotlin/com/mikeyphw/dustui/go/DomainAuthorityProductionReachabilityContractTest.kt")

    go_sources = ""
    go_internal = ROOT / "go/internal"
    if go_internal.exists():
        go_sources = "\n".join(
            path.read_text(errors="replace")
            for path in go_internal.glob("**/*.go")
            if path.is_file()
        )

    compatibility_terms = [
        "completeScan",
        "CompleteScan",
        "fullScan",
        "FullScan",
        "wholeScan",
        "WholeScan",
    ]

    compatibility_surface_present = has_any(main_vm + "\n" + repo, compatibility_terms)

    evidence = {
        "go_query_package": exists("go/internal/query"),
        "go_scan_package": exists("go/internal/scan"),
        "go_state_package": exists("go/internal/state"),
        "bounded_repository_shape": has_any(
            repo,
            [
                "page",
                "Page",
                "limit",
                "Limit",
                "offset",
                "Offset",
                "Flow<",
                "callbackFlow",
                "requestId",
                "generation",
            ],
        ),
        "request_owned_viewmodel_shape": has_any(
            main_vm,
            [
                "requestId",
                "generation",
                "Generation",
                "flatMapLatest",
                "collectLatest",
                "MutableStateFlow",
                "StateFlow",
            ],
        ),
        "db_contract_records_boundedness": has_any(
            db_contract,
            [
                "avoids repeated full scan reloads",
                "request owned",
                "generation",
                "buffered instead of flushed per entry",
            ],
        ),
        "go_authority_contract": has_any(
            go_contract,
            [
                "production reachability",
                "migratedProductTruth",
                "NoProductionKotlinAnalyzerCaller",
                "GoAuthority",
                "Go authority",
            ],
        ),
        "go_scan_is_buffered_or_streamed": has_any(
            go_sources,
            [
                "bufio.NewWriter",
                "Flush()",
                "json.NewEncoder",
                "Encoder",
                "LIMIT",
                "OFFSET",
                "limit",
                "offset",
                "page",
            ],
        ),
    }

    failures: list[str] = []
    required = [
        "go_query_package",
        "go_scan_package",
        "go_state_package",
        "bounded_repository_shape",
        "request_owned_viewmodel_shape",
    ]

    for key in required:
        if not evidence[key]:
            failures.append(f"AUD002_MISSING_{key.upper()}")

    if compatibility_surface_present and sum(1 for value in evidence.values() if value) < 5:
        failures.append("AUD007_COMPAT_COMPLETE_SCAN_WITHOUT_SUCCESSOR_BOUNDED_PROOF")

    print(f"AUD-FIX-02 leaf pass 1: failures={len(failures)}")
    for failure in failures:
        print(f"  - {failure}")
    print(f"converged={'true' if not failures else 'false'}")

    out = ROOT / "build/evidence/audfix02"
    out.mkdir(parents=True, exist_ok=True)
    (out / "bounded-successor-contract.json").write_text(
        json.dumps(
            {
                "schema": "dustui.audfix02.bounded-successor-contract.v2",
                "status": "pass" if not failures else "fail",
                "failures": failures,
                "compatibilitySurfacePresent": compatibility_surface_present,
                "evidence": evidence,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n"
    )

    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
