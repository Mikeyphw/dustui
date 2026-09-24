#!/usr/bin/env python3
"""Validate DustUI OV08 mutation/recovery destructive cutover contracts."""
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
    require(am.get('migration_phase')=='OV08','dustui_android migration_phase must be OV08',failures)
    require(gm.get('migration_phase')=='OV08','dustui_go migration_phase must be OV08',failures)
    require(am.get('android_buildability')=='intentionally-suspended','Android buildability must remain intentionally suspended',failures)
    require(am.get('android_buildability_suspended_until')=='OV13','Android reconnect gate must remain OV13',failures)
    require(am.get('gate')=='Gate C destructive safety','OV08 must declare Gate C destructive safety',failures)
    require(gm.get('mutation_authority') is True,'OV08 must claim Go mutation authority',failures)
    require(gm.get('legacy_destructive_executor')=='none','legacy destructive executor must be none',failures)
    require('jobs' not in android and 'workflows' not in android,'unsupported Devtool jobs/workflows must not be reintroduced',failures)
    for rel in ['go/internal/cleanup/cleanup.go','go/internal/cleanup/mutation_test.go','go/internal/privileged/boundary.go','go/internal/state/schema.go','go/internal/readiness/readiness.go','go/internal/runtime/spec.go','go/cmd/dustui-go/main.go']:
        require((ROOT/rel).is_file(),f'OV08 file missing: {rel}',failures)
    cleanup=run_json(['go','run','./cmd/dustui-go','mutation-contract'])
    require(cleanup.get('mutationProtocol')=='dustui.cleanup.mutation.v1','mutation protocol mismatch',failures)
    require(cleanup.get('mutationAuthority') is True,'OV08 cleanup contract must claim mutation authority',failures)
    require(cleanup.get('productionExecutor') is True,'OV08 cleanup contract must be production executor',failures)
    require(cleanup.get('legacyExecutorAuthority')=='none','legacy executor authority must be none',failures)
    require(cleanup.get('activityRunView')=='canonical','Activity must be canonical cleanup run view',failures)
    for state in ['reviewed','claimed','admitted','executing','verifying','complete','failed','interrupted','recovery_required','cleanup_debt']:
        require(state in cleanup.get('mutationStates',[]),f'mutation state missing: {state}',failures)
    for rule in ['pre_mutation_identity_revalidation','mount_boundary_enforced','protected_descendant_rejected','retained_copy_required','no_unreviewed_recursive_expansion','post_mutation_verification','stale_after_cleanup','global_destructive_single_flight','legacy_destructive_executor_unreachable']:
        require(rule in cleanup.get('safetyRules',[]),f'OV08 safety rule missing: {rule}',failures)
    readiness=run_json(['go','run','./cmd/dustui-go','readiness'])
    require(readiness.get('migrationPhase')=='OV08','readiness phase must be OV08',failures)
    require(readiness.get('mutationAuthority') is True,'readiness must expose mutation authority',failures)
    require(readiness.get('productionEngine') is False,'Android production engine must remain suspended until OV13',failures)
    runtime=run_json(['go','run','./cmd/dustui-go','runtime-contract'])
    require(runtime.get('cleanupAuthority')=='go-production-mutation-authority-ov08','runtime cleanup authority must be production Go mutation authority',failures)
    privileged=run_json(['go','run','./cmd/dustui-go','privileged-contract'])
    require(privileged.get('productionMutation') is True,'privileged diagnostics must expose productionMutation true',failures)
    ops={op.get('name'):op for op in privileged.get('operations',[])}
    require('cleanup.execute' in ops,'privileged root boundary must allow cleanup.execute',failures)
    if 'cleanup.execute' in ops:
        require('capabilityId' in ops['cleanup.execute'].get('allowedPayloadFields',[]),'cleanup.execute must require capability payload binding',failures)
    require(privileged.get('genericShell') is False,'generic shell must remain forbidden',failures)
    schema=run_json(['go','run','./cmd/dustui-go','schema'])
    require(schema.get('schemaVersion')==5,'OV08 schema version must be 5',failures)
    tables=schema.get('requiredTables',[])
    for table in ['cleanup_mutation_journal','cleanup_verification_results','cleanup_recovery_status']:
        require(table in tables,f'schema must include {table}',failures)
    joined='\n'.join(schema.get('createStatements',[]))
    for token in ['mutation_protocol','verified_absent','stale_after_cleanup','legacy_destructive_executor','no_replay']:
        require(token in joined,f'OV08 schema token missing: {token}',failures)
    for rel in ['migration/contracts/v1/mutation_recovery_gateC_ov08.md','migration/contracts/v1/authority_transition_ov08.md','migration/contracts/v1/legacy_paths_ov08.md','migration/contracts/v1/source_token_retirement_ov08.md','migration/audit/v1/ov08_audit_loop.md','migration/ledger/ov08_status.json']:
        require((ROOT/rel).is_file(),f'OV08 contract missing: {rel}',failures)
    findings=json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase')=='OV08','migration ledger phase must advance to OV08',failures)
    by_id={item.get('id'):item for item in findings.get('findings',[])}
    for fid in ['RC-04','RC-05','RC-06','N-03','N-04','N-06','ABH-05','ABH-14']:
        require(fid in by_id and 'OV08' in by_id[fid].get('overlays',[]),f'OV08 ledger mapping missing: {fid}',failures)
        require(by_id[fid].get('ov08_status'),f'OV08 ledger status missing: {fid}',failures)
    if audit_loop:
        combined='\n'.join(read(rel) for rel in ['migration/contracts/v1/mutation_recovery_gateC_ov08.md','migration/contracts/v1/authority_transition_ov08.md','migration/contracts/v1/legacy_paths_ov08.md','migration/contracts/v1/source_token_retirement_ov08.md','migration/audit/v1/ov08_audit_loop.md'])
        for phrase in ['Go is the only destructive authority','single-use','pre-mutation identity','mount-boundary','protected descendant','retained-copy','post-mutation verification','status/reconcile','legacy destructive executor is unreachable','Android buildability remains intentionally suspended']:
            require(phrase in combined,f'OV08 audit loop missing: {phrase}',failures)
    return failures

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--audit-loop',action='store_true'); args=parser.parse_args()
    failures=validate(args.audit_loop)
    if failures:
        for f in failures: print(f'OV08 contract failure: {f}',file=sys.stderr)
        return 1
    print('DustUI OV08 mutation/recovery Gate C contract passed')
    return 0
if __name__=='__main__': raise SystemExit(main())
