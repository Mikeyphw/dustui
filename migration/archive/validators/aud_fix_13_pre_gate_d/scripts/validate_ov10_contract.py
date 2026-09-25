#!/usr/bin/env python3
"""Validate DustUI OV10 cleanup intelligence contracts."""
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
    android=targets.get('dustui_android',{}); go=targets.get('dustui_go',{})
    am=android.get('metadata',{}); gm=go.get('metadata',{})
    require(am.get('migration_phase')=='OV10','dustui_android migration_phase must be OV10',failures)
    require(gm.get('migration_phase')=='OV10','dustui_go migration_phase must be OV10',failures)
    require(am.get('android_buildability')=='intentionally-suspended','Android buildability must remain intentionally suspended',failures)
    require(am.get('android_buildability_suspended_until')=='OV13','Android reconnect gate must remain OV13',failures)
    require(gm.get('cleanup_intelligence_authority') is True,'OV10 must claim cleanup intelligence authority',failures)
    require(gm.get('cleanup_intelligence_protocol')=='dustui.cleanup.intelligence.v1','cleanup intelligence protocol mismatch',failures)
    require('jobs' not in android and 'workflows' not in android,'unsupported Devtool jobs/workflows must not be reintroduced',failures)
    for rel in ['go/internal/intelligence/intelligence.go','go/internal/intelligence/intelligence_test.go','android/app/src/main/kotlin/com/mikeyphw/dustui/go/CleanupIntelligenceContract.kt','go/internal/state/schema.go','go/internal/readiness/readiness.go','go/cmd/dustui-go/main.go']:
        require((ROOT/rel).is_file(),f'OV10 file missing: {rel}',failures)
    contract=run_json(['go','run','./cmd/dustui-go','cleanup-intelligence-contract'])
    require(contract.get('protocol')=='dustui.cleanup.intelligence.v1','cleanup intelligence protocol mismatch',failures)
    require(contract.get('phase')=='OV10','cleanup intelligence phase must be OV10',failures)
    require(contract.get('goAuthority') is True,'cleanup intelligence must claim Go authority',failures)
    for domain in ['duplicates','residue','recommendations','rules']:
        require(domain in contract.get('domains',[]),f'cleanup intelligence domain missing: {domain}',failures)
    for rule in ['candidate_grouping','logical_size_semantics','content_identity_verification','canonical_keep_selection','retained_copy_proof','actionable_evidence_output']:
        require(rule in contract.get('duplicateRules',[]),f'duplicate rule missing: {rule}',failures)
    for rule in ['package_user_evidence_required','action_time_reevaluation','ambiguity_confidence','special_path_context','exclusions_protection','actionable_evidence_output']:
        require(rule in contract.get('residueRules',[]),f'residue rule missing: {rule}',failures)
    for rule in ['proof_bearing_outputs','bounded_imports','bounded_regex_execution','provenance_preserving_unions','plan_level_conflict_detection','protected_descendant_retained_copy_validation']:
        require(rule in contract.get('recommendationRules',[]),f'recommendation/rule invariant missing: {rule}',failures)
    for rule in ['bare_path_never_verified_safe','no_specialized_label_without_evidence','no_regex_only_authorization','no_unbounded_pattern_execution']:
        require(rule in contract.get('safetyRules',[]),f'safety rule missing: {rule}',failures)
    readiness=run_json(['go','run','./cmd/dustui-go','readiness'])
    require(readiness.get('migrationPhase')=='OV10','readiness phase must be OV10',failures)
    require(readiness.get('cleanupIntelligence') is True,'readiness must expose cleanup intelligence',failures)
    require(readiness.get('productionEngine') is False,'Android production engine must remain suspended until OV13',failures)
    schema=run_json(['go','run','./cmd/dustui-go','schema'])
    require(schema.get('schemaVersion')==7,'OV10 schema version must be 7',failures)
    tables=schema.get('requiredTables',[])
    for table in ['cleanup_duplicate_groups','cleanup_duplicate_candidates','cleanup_residue_evidence','cleanup_recommendations','cleanup_rule_results','cleanup_plan_conflicts']:
        require(table in tables,f'schema must include {table}',failures)
    joined='\n'.join(schema.get('createStatements',[]))
    for token in ['verification_state','canonical_keep_path','retained_copy_proof','actionable_evidence_json','bounded_regex','provenance_json','requires_review']:
        require(token in joined,f'OV10 schema token missing: {token}',failures)
    for rel in ['migration/contracts/v1/cleanup_intelligence_ov10.md','migration/contracts/v1/authority_transition_ov10.md','migration/contracts/v1/legacy_paths_ov10.md','migration/contracts/v1/source_token_retirement_ov10.md','migration/audit/v1/ov10_audit_loop.md','migration/ledger/ov10_status.json']:
        require((ROOT/rel).is_file(),f'OV10 contract missing: {rel}',failures)
    findings=json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase')=='OV10','migration ledger phase must advance to OV10',failures)
    by_id={item.get('id'):item for item in findings.get('findings',[])}
    for fid in ['RC-03','RC-09','ABH-10','ABH-11','ABH-13']:
        require(fid in by_id and 'OV10' in by_id[fid].get('overlays',[]),f'OV10 ledger mapping missing: {fid}',failures)
        require(by_id[fid].get('ov10_status'),f'OV10 ledger status missing: {fid}',failures)
    if audit_loop:
        combined='\n'.join(read(rel) for rel in ['migration/contracts/v1/cleanup_intelligence_ov10.md','migration/contracts/v1/authority_transition_ov10.md','migration/contracts/v1/legacy_paths_ov10.md','migration/contracts/v1/source_token_retirement_ov10.md','migration/audit/v1/ov10_audit_loop.md'])
        for phrase in ['Cleanup intelligence is owned by Go','No specialized result can degrade to a bare path','bounded regex','plan-level conflict detection','Android buildability remains intentionally suspended']:
            require(phrase in combined,f'OV10 audit loop missing: {phrase}',failures)
    return failures

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--audit-loop',action='store_true'); args=parser.parse_args()
    failures=validate(args.audit_loop)
    if failures:
        for f in failures: print(f'OV10 contract failure: {f}',file=sys.stderr)
        return 1
    print('DustUI OV10 cleanup intelligence contract passed')
    return 0
if __name__=='__main__': raise SystemExit(main())
