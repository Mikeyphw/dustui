#!/usr/bin/env python3
"""Validate DustUI OV07 cleanup manifest/capability contracts."""
from __future__ import annotations
import argparse, json, subprocess, sys, tomllib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read(rel: str) -> str: return (ROOT / rel).read_text(encoding="utf-8")
def require(cond: bool, msg: str, failures: list[str]) -> None:
    if not cond: failures.append(msg)
def run_json(args: list[str]) -> dict:
    proc = subprocess.run(args, cwd=ROOT / "go", text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proc.returncode != 0: raise RuntimeError(proc.stderr or proc.stdout)
    return json.loads(proc.stdout)

def validate(audit_loop: bool=False) -> list[str]:
    failures=[]
    devtool=tomllib.loads(read('.devtool.toml'))
    targets=devtool.get('targets',{})
    android=targets.get('dustui_android',{})
    go=targets.get('dustui_go',{})
    am=android.get('metadata',{}); gm=go.get('metadata',{})
    require(am.get('migration_phase')=='OV07','dustui_android migration_phase must be OV07',failures)
    require(gm.get('migration_phase')=='OV07','dustui_go migration_phase must be OV07',failures)
    require(am.get('android_buildability')=='intentionally-suspended','Android buildability must remain intentionally suspended',failures)
    require(am.get('android_buildability_suspended_until')=='OV13','Android reconnect gate must remain OV13',failures)
    require(gm.get('cleanup_manifest_capability') is True,'cleanup manifest/capability metadata missing',failures)
    require(gm.get('mutation_authority') is False,'OV07 must not claim mutation authority',failures)
    require('jobs' not in android and 'workflows' not in android,'unsupported Devtool jobs/workflows must not be reintroduced',failures)
    for rel in ['go/internal/cleanup/cleanup.go','go/internal/cleanup/cleanup_test.go','go/internal/state/schema.go','go/cmd/dustui-go/main.go']:
        require((ROOT/rel).is_file(),f'OV07 file missing: {rel}',failures)
    contract=run_json(['go','run','./cmd/dustui-go','cleanup-contract'])
    require(contract.get('protocol')=='dustui.cleanup.capability.v1','cleanup protocol mismatch',failures)
    require(contract.get('mutationAuthority') is False,'OV07 cleanup contract must not claim mutation authority',failures)
    require(contract.get('productionExecutor') is False,'OV07 must not be production executor',failures)
    require(contract.get('cutoverOverlay')=='OV08','cleanup cutover overlay must remain OV08',failures)
    for field in ['path','scanId','scanGeneration','sourceSurface','sourceKind','expectedHash','expectedIdentity','retainedCopyConstraint','packageName','userHandle','bucket','protectedState','duplicateGroupId','ruleId','recommendationId','deletionEligible']:
        require(field in contract.get('actionableEvidenceFields',[]),f'actionable evidence missing {field}',failures)
    for field in ['exactDescendants','pathRole','protection','mountBoundary','identityEvidence','manifestHash','manifestRevision','sourceContextHash','rootIdentity']:
        require(field in contract.get('manifestFields',[]),f'manifest field missing {field}',failures)
    for field in ['capabilityId','operationId','scanId','scanGeneration','scanContextHash','manifestHash','manifestRevision','rootIdentity','reviewedAt','expiresAt','claimState']:
        require(field in contract.get('capabilityFields',[]),f'capability field missing {field}',failures)
    for rule in ['identity_change_invalidates_authorization','added_descendant_after_review_not_approved','capability_single_use','expired_capability_fails','lost_response_requires_status_reconcile','bare_paths_cannot_claim_specialized_labels']:
        require(rule in contract.get('safetyRules',[]),f'safety rule missing {rule}',failures)
    readiness=run_json(['go','run','./cmd/dustui-go','readiness'])
    require(readiness.get('migrationPhase')=='OV07','readiness phase must be OV07',failures)
    require(readiness.get('cleanupCapability') is True,'readiness must expose cleanup capability',failures)
    require(readiness.get('mutationAuthority') is False,'OV07 readiness must not claim mutation authority',failures)
    runtime=run_json(['go','run','./cmd/dustui-go','runtime-contract'])
    require(runtime.get('cleanupAuthority')=='go-evidence-capability-non-mutating-until-OV08','runtime cleanup authority must be non-mutating until OV08',failures)
    schema=run_json(['go','run','./cmd/dustui-go','schema'])
    require(schema.get('schemaVersion')==4,'OV07 schema version must be 4',failures)
    tables=schema.get('requiredTables',[])
    for table in ['cleanup_actionable_evidence','cleanup_manifest_entries','cleanup_capability_claims']:
        require(table in tables,f'schema must include {table}',failures)
    joined='\n'.join(schema.get('createStatements',[]))
    for token in ['manifest_revision','scan_context_hash','root_identity','claim_state','exact_descendants_json','reconcile_required']:
        require(token in joined,f'schema missing cleanup token {token}',failures)
    for rel in ['migration/contracts/v1/cleanup_manifest_capability_ov07.md','migration/contracts/v1/review_flow_ov07.md','migration/contracts/v1/authority_transition_ov07.md','migration/contracts/v1/legacy_paths_ov07.md','migration/contracts/v1/source_token_retirement_ov07.md','migration/audit/v1/ov07_audit_loop.md','migration/ledger/ov07_status.json']:
        require((ROOT/rel).is_file(),f'OV07 contract missing: {rel}',failures)
    findings=json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase')=='OV07','migration ledger phase must advance to OV07',failures)
    by_id={item.get('id'):item for item in findings.get('findings',[])}
    for fid in ['RC-03','RC-04','N-06']:
        require(fid in by_id and 'OV07' in by_id[fid].get('overlays',[]),f'OV07 ledger mapping missing: {fid}',failures)
        require(by_id[fid].get('ov07_status'),f'OV07 ledger status missing: {fid}',failures)
    if audit_loop:
        combined='\n'.join(read(rel) for rel in ['migration/contracts/v1/cleanup_manifest_capability_ov07.md','migration/contracts/v1/review_flow_ov07.md','migration/contracts/v1/authority_transition_ov07.md','migration/contracts/v1/legacy_paths_ov07.md','migration/contracts/v1/source_token_retirement_ov07.md','migration/audit/v1/ov07_audit_loop.md'])
        for phrase in ['immutable','one-use','exact descendants','status/reconcile','bare paths','OV08','intentionally suspended']:
            require(phrase in combined,f'OV07 audit loop missing: {phrase}',failures)
    return failures

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--audit-loop',action='store_true'); args=parser.parse_args()
    failures=validate(args.audit_loop)
    if failures:
        for f in failures: print(f'OV07 contract failure: {f}',file=sys.stderr)
        return 1
    print('DustUI OV07 cleanup manifest/capability contract passed')
    return 0
if __name__=='__main__': raise SystemExit(main())
