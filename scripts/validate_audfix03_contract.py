#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "migration/audit/v2/aud_fix_03_audit_loop.json"
LEDGER = ROOT / "migration/ledger/aud_fix_03_status.json"


def text(rel: str) -> str:
    p = ROOT / rel
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def production_kotlin() -> str:
    root = ROOT / "android/app/src/main/kotlin"
    return "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in root.rglob("*.kt"))


def check_source() -> list[dict[str, str]]:
    failures: list[dict[str, str]] = []
    def req(ok: bool, code: str, detail: str) -> None:
        if not ok:
            failures.append({"code": code, "detail": detail})

    contract = text("go/internal/authority/contract.go")
    runtime = text("go/cmd/dustui-go/runtime_domain.go")
    state = text("go/internal/state/domain_authority.go")
    state_test = text("go/internal/state/domain_authority_test.go")
    schema = text("go/internal/state/schema.go")
    gomod = text("go/go.mod")
    gradle = text("android/app/build.gradle.kts")
    domain = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoDomainStore.kt")
    repo = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
    app_model = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/AppStorageInspector.kt")
    app_ui = text("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/AppStorageInspectorScreen.kt")
    mapper = text("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoProductAuthorityMapper.kt")
    prod = production_kotlin()

    for token in ["dustui.domain.authority.request.v1", "dustui.domain.authority.response.v1", "EvidenceSupported", "EvidencePartial", "EvidenceUnavailable", "EvidenceBlocked", "Bounded", "Durable", 'Source             string']:
        req(token in contract, "AUD008_TYPED_PROTOCOL", f"authority protocol missing {token}")
    req("ValidateRequest" in contract and "scan_generation" in contract, "AUD008_PROTOCOL_BINDING", "typed request does not bind scan/generation")
    req("typedAuthority(" in domain and "DOMAIN_AUTHORITY_REQUEST_SCHEMA" in domain and "DOMAIN_AUTHORITY_RESPONSE_SCHEMA" in domain,
        "AUD008_ANDROID_PROTOCOL", "Android typed authority protocol missing")
    req("GoDomainAuthorityStatus" in domain and "fun status(): GoDomainAuthorityStatus" in domain and "authorityStatuses" in repo and "domainAuthorityStatus" in repo,
        "AUD008_EVIDENCE_END_TO_END", "Android adapter drops freshness/evidence/degradation metadata before the UI boundary")
    for token in ['authority.getString("domain") == domain', 'authority.getString("scanId") == scanId', 'authority.getLong("generation") == generation', 'authority.optBoolean("bounded")', 'authority.optBoolean("durable")']:
        req(token in domain, "AUD008_ANDROID_RESPONSE_GUARD", f"Android authority guard missing: {token}")

    for op in [
        "domain.authority.query", "domain.authority.rules.preview", "domain.package.snapshot.replace", "domain.package.snapshot.latest",
        "domain.authority.duplicates.replace", "domain.cleanup.verification.build", "domain.cleanup.verification.reverify",
        "domain.cleanup.verification.get", "domain.cleanup.verification.list",
    ]:
        req(op in runtime, "AUD008_RUNTIME_OPERATION", f"Go runtime operation missing: {op}")
    for domain_name in ["package-storage", "residue", "cleanup-intelligence", "recommendations", "growth", "modules"]:
        req(f'"{domain_name}"' in repo, "AUD008_ANDROID_DOMAIN_REACHABILITY", f"Android does not consume typed Go domain {domain_name}")
    req('"domain.product.insights"' not in repo and '"domain.product.rules.preview"' not in repo,
        "AUD006_NO_GENERIC_PREDECESSOR", "Android still calls predecessor generic product authority")

    for forbidden in [
        "AppStorageInspectorEngine.analyze(", "CleanupRecommendationEngine.analyze(", "StorageGrowthEngine.compare(",
        "CleanupRuleEngine.preview(", "RootModuleAnalyzer.analyze(", "CleanupVerificationEngine.build(", "CleanupVerificationEngine.reverify(",
    ]:
        req(forbidden not in prod, "AUD006_NO_KOTLIN_DOMAIN_CALLER", f"production Kotlin still calls domain authority {forbidden}")
    for forbidden in ["cleanupReportStore.load(", "cleanupReportStore.reports(", "cleanupReportStore.report(", "cleanupReportStore.save("]:
        req(forbidden not in repo, "AUD006_VERIFICATION_GO_AUTHORITY", f"cleanup report file cache remains authoritative: {forbidden}")
    req("domain.cleanup.verification.list" in repo and "domain.cleanup.verification.get" in repo,
        "AUD006_VERIFICATION_RECONNECT", "Android does not reconnect verification reports through Go")
    req(repo.count('GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA') >= 4 and '"domain", "cleanup-verification"' in repo,
        "AUD008_VERIFICATION_SCHEMA", "cleanup verification requests are not versioned end-to-end")
    req('p.Schema != authority.RequestSchema' in runtime and 'cleanup-verification-build' in runtime and 'cleanup-verification-reverify' in runtime and 'cleanup-verification-list' in runtime,
        "AUD008_VERIFICATION_RUNTIME_GUARD", "Go verification runtime does not reject malformed/old-schema requests")

    req("PackageEvidenceForScan(ctx, req.ScanID, req.Generation)" in state, "AUD008_PACKAGE_SCAN_BINDING", "typed package evidence is not bound to scan identity and generation")
    req("PackageEvidenceForScan(ctx, scanID, session.Generation)" in text("go/internal/state/product_views_bounded.go"), "AUD008_PRODUCT_SCAN_BINDING", "bounded product projection can consume another scan's package evidence")
    req("ReplacePackageEvidence" in text("go/internal/state/product_authority_core.go") and "scan_id" in text("go/internal/state/product_authority_core.go"), "AUD008_PACKAGE_PERSISTENCE", "durable package evidence is not scan-bound in SQLite")
    req("PersistVerificationReport" in state and "VerificationReports" in state,
        "AUD008_VERIFICATION_PERSISTENCE", "cleanup verification is not durable/listable in Go")
    for token in ["android_package_evidence_snapshots", "cleanup_duplicate_groups", "cleanup_residue_evidence", "cleanup_recommendations", "cleanup_rule_results", "android_cleanup_verification_reports"]:
        req(token in schema, "AUD014_PERSISTENCE_PREREQUISITE", f"Go persistence table missing: {token}")
    req(re.search(r"const SchemaVersion = (?:1[3-9]|[2-9][0-9]+)", schema) is not None and "scan_id TEXT NOT NULL" in schema, "AUD014_SCHEMA_VERSION", "AUD-FIX-03 schema must be v13+ with scan-bound package evidence")
    for token in ["store.Close()", "OpenSQLiteStore(path)", "EvidencePartial", "VerificationReports(ctx, 100)", "stale.Generation = 6", "cross-scan evidence bleed"]:
        req(token in state_test, "AUD014_RESTART_FIXTURE", f"restart parity fixture missing {token}")

    req("modernc.org/sqlite" in gomod and "mattn/go-sqlite" not in gomod, "AUD014_SQLITE_DRIVER", "Go authority must remain on modernc SQLite")
    req('CGO_ENABLED", "0"' in gradle, "AUD014_CGO_FREE", "Android Go runtime must remain CGO-free")

    req("AppStorageEvidenceState" in app_model and "evidenceState" in app_model and "evidenceReasons" in app_model,
        "UIX007_EVIDENCE_MODEL", "App storage report cannot carry evidence completeness")
    req("evidenceState" in mapper and "evidenceReasons" in mapper, "UIX007_EVIDENCE_MAPPING", "Go evidence completeness is dropped during Android mapping")
    req('StatusPill("evidence ' in app_ui and "evidenceReasons" in app_ui,
        "UIX007_EVIDENCE_UI", "Apps UI does not render evidence state/reasons")
    req('collectionCompleteness", "partial"' in repo and 'package_byte_counts_not_available' in repo and '.put("known", false)' in repo,
        "UIX007_PLATFORM_OBSERVATION_TRUTH", "platform package collector can masquerade unavailable byte evidence as known")

    fixture = text("migration/contracts/v2/fixtures/domain_authority_golden_v1.json")
    go_golden = text("go/internal/authority/contract_test.go")
    kotlin_golden = text("android/app/src/test/kotlin/com/mikeyphw/dustui/go/DomainAuthorityGoldenContractTest.kt")
    req("dustui.domain.authority.golden.v1" in fixture and "rejectedRequestSchemas" in fixture,
        "AUD008_GOLDEN_FIXTURE", "shared cross-language golden fixture missing")
    req("TestAuthorityGoldenFixtureIsSharedAndVersioned" in go_golden and "DomainAuthorityGoldenContractTest" in kotlin_golden,
        "AUD008_CROSS_LANGUAGE_GOLDEN", "Go/Android do not share the authority golden fixture")

    qual = ROOT / "migration/qualification/v1/current_state.json"
    if qual.is_file():
        obj = json.loads(qual.read_text())
        req(obj.get("gate_d", {}).get("qualified") is False,
            "ROADMAP_GATE_D_MUST_STAY_OPEN", "AUD-FIX-03 must not qualify Gate D")
    return failures


def run_go_tests() -> tuple[bool, str]:
    cp = subprocess.run(
        ["go", "test", "./internal/authority", "./internal/packageevidence", "./internal/intelligence", "./internal/scan", "./internal/rootworker"],
        cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return cp.returncode == 0, cp.stdout[-16000:]


def modernc_available() -> bool:
    cp = subprocess.run(["go", "env", "GOMODCACHE"], cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    if cp.returncode != 0:
        return False
    cache = Path(cp.stdout.strip())
    return any(cache.glob("modernc.org/sqlite@v1.36.3*")) or any((cache / "cache/download/modernc.org/sqlite/@v").glob("v1.36.3.zip"))


def run_restart_test() -> dict:
    if not modernc_available():
        return {"status": "environment-deferred", "reason": "modernc.org/sqlite v1.36.3 is not present in the local module cache; CGO substitution is forbidden"}
    cp = subprocess.run(
        ["go", "test", "./internal/state", "-run", "^TestAUD_FIX03AuthoritySurvivesRestartWithGenerationAndEvidenceTruth$", "-count=1"],
        cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )
    return {"status": "passed" if cp.returncode == 0 else "failed", "output": cp.stdout[-16000:]}


def run_pass(index: int) -> dict:
    failures = check_source()
    ok, out = run_go_tests()
    if not ok:
        failures.append({"code": "AUD_FIX_03_GO_TESTS", "detail": "targeted authority/package/intelligence/scanner Go tests failed"})
    restart = run_restart_test()
    if restart["status"] == "failed":
        failures.append({"code": "AUD_FIX_03_RESTART_TEST", "detail": "SQLite restart parity test failed"})
    return {"pass": index, "failures": failures, "go_tests": {"ok": ok, "output": out}, "restart_test": restart}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-loop", action="store_true")
    ap.add_argument("--write-evidence", action="store_true")
    args = ap.parse_args()
    passes=[]; previous=None; converged=False
    for i in range(1, 5 if args.audit_loop else 2):
        result=run_pass(i); passes.append(result)
        codes=tuple(x["code"] for x in result["failures"])
        if not codes and previous == codes:
            converged=True; break
        if not args.audit_loop and not codes:
            converged=True; break
        if previous == codes:
            converged=not codes; break
        previous=codes
    failures=passes[-1]["failures"]
    deferred=passes[-1]["restart_test"].get("status") == "environment-deferred"
    evidence={
        "schema":"dustui.aud-fix-03-audit.v1", "overlay":"AUD-FIX-03",
        "scope_gap_families":["AUD-006","AUD-008","AUD-014","UIX-007"],
        "passes":passes, "converged":converged and not failures,
        "environment_deferred_evidence":["modernc SQLite restart test"] if deferred else [],
        "later_roadmap_gaps_intentionally_open":["AUD-003","AUD-004","AUD-005","AUD-011","AUD-012","AUD-013","AUD-015","AUD-016","AUD-017","AUD-018","AUD-019","AUD-020","AUD-021","AUD-022","AUD-023","AUD-024","UIX-001","UIX-002","UIX-003","UIX-004","UIX-005","UIX-006","UIX-008","UIX-009"],
        "gate_d":"open",
    }
    ledger={
        "schema":"dustui.aud-fix-status.v1", "overlay":"AUD-FIX-03",
        "status":"implementation-complete-evidence-deferred" if evidence["converged"] and deferred else ("complete" if evidence["converged"] else "blocked"),
        "closed_or_advanced_gap_families":["AUD-006","AUD-008","AUD-014","UIX-007"] if evidence["converged"] else [],
        "audit_evidence":"migration/audit/v2/aud_fix_03_audit_loop.json",
        "gate_d":"open", "passes":len(passes), "blockers":failures,
        "environment_deferred_evidence":evidence["environment_deferred_evidence"],
    }
    if args.write_evidence:
        EVIDENCE.parent.mkdir(parents=True,exist_ok=True); LEDGER.parent.mkdir(parents=True,exist_ok=True)
        EVIDENCE.write_text(json.dumps(evidence,indent=2)+"\n")
        LEDGER.write_text(json.dumps(ledger,indent=2)+"\n")
    for p in passes:
        print(f"AUD-FIX-03 audit pass {p['pass']}: failures={len(p['failures'])} restart={p['restart_test']['status']}")
        for f in p["failures"]: print(f"  - {f['code']}: {f['detail']}")
    print(f"converged={str(evidence['converged']).lower()}")
    return 0 if evidence["converged"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
