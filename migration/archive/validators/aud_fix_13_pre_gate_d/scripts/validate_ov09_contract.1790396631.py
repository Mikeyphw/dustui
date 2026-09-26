#!/usr/bin/env python3
"""Validate DustUI OV09 package/storage evidence authority contracts."""
from __future__ import annotations
import argparse, json, subprocess, sys, tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str: return (ROOT / rel).read_text(encoding='utf-8')
def require(cond: bool, msg: str, failures: list[str]) -> None:
    if not cond: failures.append(msg)
def run_json(args: list[str]) -> dict:
    proc = subprocess.run(args, cwd=ROOT / 'go', text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0: raise RuntimeError(proc.stderr or proc.stdout)
    return json.loads(proc.stdout)

def validate(audit_loop: bool=False) -> list[str]:
    failures=[]
    devtool=tomllib.loads(read('.devtool.toml'))
    targets=devtool.get('targets',{})
    android=targets.get('dustui_android',{})
    go=targets.get('dustui_go',{})
    am=android.get('metadata',{}); gm=go.get('metadata',{})
    require(am.get('migration_phase')=='OV09','dustui_android migration_phase must be OV09',failures)
    require(gm.get('migration_phase')=='OV09','dustui_go migration_phase must be OV09',failures)
    require(am.get('android_buildability')=='intentionally-suspended','Android buildability must remain intentionally suspended',failures)
    require(am.get('android_buildability_suspended_until')=='OV13','Android reconnect gate must remain OV13',failures)
    require(gm.get('package_storage_authority') is True,'OV09 must claim package_storage_authority',failures)
    require(gm.get('package_storage_protocol')=='dustui.package.storage.evidence.v1','package storage protocol mismatch',failures)
    require('jobs' not in android and 'workflows' not in android,'unsupported Devtool jobs/workflows must not be reintroduced',failures)
    for rel in ['go/internal/packageevidence/packageevidence.go','go/internal/packageevidence/packageevidence_test.go','android/app/src/main/kotlin/com/mikeyphw/dustui/go/PackageStorageEvidenceContract.kt','go/internal/state/schema.go','go/internal/readiness/readiness.go','go/cmd/dustui-go/main.go']:
        require((ROOT/rel).is_file(),f'OV09 file missing: {rel}',failures)
    pkg=run_json(['go','run','./cmd/dustui-go','package-storage-contract'])
    require(pkg.get('protocol')=='dustui.package.storage.evidence.v1','package storage protocol mismatch',failures)
    require(pkg.get('phase')=='OV09','package storage contract phase must be OV09',failures)
    require(pkg.get('goAuthority') is True,'package storage contract must claim Go authority',failures)
    for state in ['supported','partial','unavailable','blocked']:
        require(state in pkg.get('evidenceStates',[]),f'evidence state missing: {state}',failures)
        require(state in pkg.get('uiStates',[]),f'UI state missing: {state}',failures)
    for fact in ['package/user inventory','installed/archived','system app','enabled state','source/split dirs','CE/DE data','external/adoptable/secondary storage','code/data/cache/native paths','collection completeness/degradation reasons']:
        require(fact in pkg.get('packageFacts',[]),f'package fact missing: {fact}',failures)
    for rule in ['package_user_evidence_required','path_regex_never_sufficient','partial_evidence_not_zero','action_time_recheck_required','multi_user_keys_are_distinct','system_or_enabled_packages_block_deletion']:
        require(rule in pkg.get('residueRules',[]),f'package residue rule missing: {rule}',failures)
    readiness=run_json(['go','run','./cmd/dustui-go','readiness'])
    require(readiness.get('migrationPhase')=='OV09','readiness phase must be OV09',failures)
    require(readiness.get('packageStorageAuthority') is True,'readiness must expose package storage authority',failures)
    require(readiness.get('productionEngine') is False,'Android production engine must remain suspended until OV13',failures)
    runtime=run_json(['go','run','./cmd/dustui-go','runtime-contract'])
    require(runtime.get('packageStorageAuthority')=='go-package-storage-evidence-authority-ov09','runtime package storage authority mismatch',failures)
    schema=run_json(['go','run','./cmd/dustui-go','schema'])
    require(schema.get('schemaVersion')==6,'OV09 schema version must be 6',failures)
    tables=schema.get('requiredTables',[])
    for table in ['android_package_evidence_snapshots','android_package_records','android_storage_evidence','package_residue_candidates','package_action_rechecks']:
        require(table in tables,f'schema must include {table}',failures)
    joined='\n'.join(schema.get('createStatements',[]))
    for token in ['evidence_hash','package_key','collection_completeness','requires_recheck','derived_from_regex']:
        require(token in joined,f'OV09 schema token missing: {token}',failures)
    for rel in ['migration/contracts/v1/package_storage_authority_ov09.md','migration/contracts/v1/authority_transition_ov09.md','migration/contracts/v1/legacy_paths_ov09.md','migration/contracts/v1/source_token_retirement_ov09.md','migration/audit/v1/ov09_audit_loop.md','migration/ledger/ov09_status.json']:
        require((ROOT/rel).is_file(),f'OV09 contract missing: {rel}',failures)
    findings=json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase')=='OV09','migration ledger phase must advance to OV09',failures)
    by_id={item.get('id'):item for item in findings.get('findings',[])}
    for fid in ['RC-08','ABH-11','ABH-12']:
        require(fid in by_id and 'OV09' in by_id[fid].get('overlays',[]),f'OV09 ledger mapping missing: {fid}',failures)
        require(by_id[fid].get('ov09_status'),f'OV09 ledger status missing: {fid}',failures)
    if audit_loop:
        combined='\n'.join(read(rel) for rel in ['migration/contracts/v1/package_storage_authority_ov09.md','migration/contracts/v1/authority_transition_ov09.md','migration/contracts/v1/legacy_paths_ov09.md','migration/contracts/v1/source_token_retirement_ov09.md','migration/audit/v1/ov09_audit_loop.md'])
        for phrase in ['package/user inventory','Partial package evidence is never rendered as zero','Path regex alone is never authoritative','action-time re-evaluation','Go is now the package/storage evidence authority','Android buildability remains intentionally suspended']:
            require(phrase in combined,f'OV09 audit loop missing: {phrase}',failures)
    return failures

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--audit-loop',action='store_true'); args=parser.parse_args()
    failures=validate(args.audit_loop)
    if failures:
        for f in failures: print(f'OV09 contract failure: {f}',file=sys.stderr)
        return 1
    print('DustUI OV09 package/storage evidence authority contract passed')
    return 0
if __name__=='__main__': raise SystemExit(main())
