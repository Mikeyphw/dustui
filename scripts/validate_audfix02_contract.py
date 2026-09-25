#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "migration/audit/v2/aud_fix_02_audit_loop.json"
LEDGER = ROOT / "migration/ledger/aud_fix_02_status.json"


def text(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def check_source() -> list[dict[str, str]]:
    failures: list[dict[str, str]] = []
    def req(ok: bool, code: str, detail: str) -> None:
        if not ok:
            failures.append({"code": code, "detail": detail})

    root_scan = text("go/internal/rootworker/scan.go")
    scanner = text("go/internal/scan/scan.go")
    duplicates = text("go/internal/rootworker/duplicates.go")
    go_main = text("go/cmd/dustui-go/main.go")
    root_service = text("android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
    go_root = text("android/app/src/main/kotlin/com/mikeyphw/dustui/root/GoRootWorker.kt")
    domain = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoDomainStore.kt")
    repo = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    vm = text("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
    ui = text("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
    query = text("go/internal/state/android_query_page.go")
    export = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ExportWriter.kt")

    req('"github.com/mikeyphw/dustui/go/internal/scan"' in root_scan and "scan.ScanContext(" in root_scan,
        "AUD009_SHARED_SCANNER", "privileged scanner must delegate traversal to internal/scan")
    req(not re.search(r"\b(?:os\.)?ReadDir\s*\(|\bWalkDir\s*\(|\bfilepath\.Walk\s*\(", root_scan),
        "AUD009_NO_SECOND_TRAVERSAL", "privileged adapter contains independent filesystem traversal")
    req("HardLinkGroup" in scanner and "FilesystemBoundaryPolicy" in scanner and "ScanContext" in scanner,
        "AUD009_SCANNER_SEMANTICS", "shared scanner lacks hard-link/boundary/context semantics")

    req("func VerifyDuplicates" in duplicates and "maxDuplicatePaths" in duplicates and "maxDuplicateInputBytes" in duplicates,
        "AUD010_GO_DUP_VERIFY", "bounded Go duplicate verifier missing")
    req("hashEdges" in duplicates and "hashWhole" in duplicates and "File changed while hashing" in duplicates,
        "AUD010_RECHECK_HASH", "duplicate verifier must edge-hash, full-hash and recheck identity")
    req("hardLinked" in duplicates and "safeReclaimableBytes" in duplicates and "autoDeleteEligible" in duplicates,
        "AUD010_HARDLINK_SAFETY", "duplicate verifier lacks physical-copy/hard-link safety")
    req("ctx.Err()" in duplicates and "encodeDuplicateCancelled" in duplicates,
        "AUD010_CANCELLATION", "duplicate verification cancellation is not typed")
    req('case "root-duplicates-verify"' in go_main,
        "AUD010_COMMAND", "root-duplicates-verify command missing")
    req("verifyDuplicates(" in go_root and '"root-duplicates-verify"' in go_root,
        "AUD010_ANDROID_IPC", "Android root adapter does not call Go duplicate verifier")
    req("GoRootWorker.verifyDuplicates" in root_service,
        "AUD010_ROOT_SERVICE_DELEGATION", "root service does not delegate duplicate verification to Go")
    req(not any(tok in root_service for tok in ["MessageDigest", "hashFileEdges", "hashWholeFile", "SHA-256"]),
        "AUD010_NO_KOTLIN_HASH_AUTHORITY", "Kotlin root service still contains duplicate hashing authority")

    req("fun browsePage(" in domain and "domain.scan.browse.page" in domain,
        "AUD007_BOUNDED_ANDROID_API", "Android Go store has no bounded browse-page API")
    req("entriesOnce(" not in domain and "observeEntries(" not in domain,
        "AUD007_NO_DOMAIN_DRAIN", "GoDomainStore still exposes whole-scan drain API")
    req("normalizedBrowseLimit" in query and "if limit > 500" in query,
        "AUD007_PAGE_LIMIT", "Go browse requests are not hard-capped")
    req("SELECT COUNT(*)" in query and "LIMIT ? OFFSET ?" in query,
        "AUD007_SQL_PAGING", "Go browse authority must count/aggregate and page in SQLite")
    req("private val allEntries" not in vm and "entriesForAnalysis(" not in vm and "allEntries.value" not in vm,
        "AUD007_NO_VM_COMPLETE_SCAN", "MainViewModel still maintains/rebuilds a complete scan")
    req("entriesForAnalysis(" not in repo and "largeFileEntries(" not in repo,
        "AUD007_NO_REPOSITORY_DRAIN", "ScanRepository still has whole-scan analysis/list APIs")
    req("writeCsvPaged" in export and "writeCsvPaged" in repo,
        "AUD007_STREAM_EXPORT", "CSV export is not paged/streaming")
    req("selection.truncated" in vm and "Refine it before selecting for deletion" in vm,
        "AUD007_SAFE_BULK_BOUND", "bounded category results can still be mistaken for complete destructive selection")
    req("previousExplorerPage" in vm and "nextExplorerPage" in vm and "explorerPage" in ui,
        "AUD007_EXPLORER_PAGING", "Explorer paging is not user-reachable")
    req("previousRootSearchPage" in vm and "nextRootSearchPage" in vm and "rootSearchPage" in ui,
        "AUD007_SEARCH_PAGING", "whole-scan search paging is not user-reachable")
    req("limit = CATEGORY_SELECTION_LIMIT" in vm and "CATEGORY_SELECTION_LIMIT = 500" in vm,
        "AUD007_CATEGORY_BOUND", "category lookup is not explicitly bounded")

    qual = ROOT / "migration/qualification/v1/current_state.json"
    if qual.is_file():
        obj = json.loads(qual.read_text())
        req(obj.get("gate_d", {}).get("qualified") is False,
            "ROADMAP_GATE_D_MUST_STAY_OPEN", "AUD-FIX-02 must not qualify Gate D")
    return failures


def run_go_tests() -> tuple[bool, str]:
    cp = subprocess.run(
        ["go", "test", "./internal/scan", "./internal/rootworker"],
        cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return cp.returncode == 0, cp.stdout[-12000:]


def run_pass(index: int) -> dict:
    failures = check_source()
    ok, out = run_go_tests()
    if not ok:
        failures.append({"code": "AUD_FIX_02_GO_TESTS", "detail": "targeted shared-scanner/root-worker Go tests failed"})
    return {"pass": index, "failures": failures, "go_tests": {"ok": ok, "output": out}}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    ap.add_argument("--write-evidence", action="store_true")
    args = ap.parse_args()
    passes=[]
    max_passes = 4 if args.audit_loop else 1
    prior_codes = None
    converged = False
    for i in range(1, max_passes+1):
        result=run_pass(i); passes.append(result)
        codes=tuple(x["code"] for x in result["failures"])
        if not codes and prior_codes == codes:
            converged=True; break
        if not codes and prior_codes is None and not args.audit_loop:
            converged=True; break
        if prior_codes == codes:
            converged=not codes
            break
        prior_codes=codes
    failures=passes[-1]["failures"]
    evidence={
        "schema":"dustui.aud-fix-02-audit.v1",
        "overlay":"AUD-FIX-02",
        "scope_gap_families":["AUD-007","AUD-009","AUD-010"],
        "passes":passes,
        "converged":converged and not failures,
        "later_roadmap_gaps_intentionally_open":["AUD-006","AUD-008","AUD-011","AUD-012","AUD-013","UIX-001","UIX-002","UIX-003","UIX-004","UIX-005","UIX-006","UIX-007","UIX-008","UIX-009"],
        "gate_d":"open",
    }
    ledger={
        "schema":"dustui.aud-fix-status.v1","overlay":"AUD-FIX-02",
        "status":"complete" if evidence["converged"] else "blocked",
        "closed_gap_families":["AUD-007","AUD-009","AUD-010"] if evidence["converged"] else [],
        "audit_evidence":"migration/audit/v2/aud_fix_02_audit_loop.json",
        "gate_d":"open","passes":len(passes),"blockers":failures,
    }
    if args.write_evidence:
        EVIDENCE.parent.mkdir(parents=True,exist_ok=True)
        EVIDENCE.write_text(json.dumps(evidence,indent=2)+"\n")
        LEDGER.write_text(json.dumps(ledger,indent=2)+"\n")
    for p in passes:
        print(f"AUD-FIX-02 audit pass {p['pass']}: failures={len(p['failures'])}")
        for f in p["failures"]: print(f"  - {f['code']}: {f['detail']}")
    print(f"converged={str(evidence['converged']).lower()}")
    return 0 if evidence["converged"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
