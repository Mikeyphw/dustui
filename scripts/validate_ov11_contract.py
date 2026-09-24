#!/usr/bin/env python3
"""Validate DustUI OV11 automation/activity ownership contracts."""
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
    require(am.get('migration_phase')=='OV11','dustui_android migration_phase must be OV11',failures)
    require(gm.get('migration_phase')=='OV11','dustui_go migration_phase must be OV11',failures)
    require(am.get('android_buildability')=='intentionally-suspended','Android buildability must remain intentionally suspended',failures)
    require(am.get('android_buildability_suspended_until')=='OV13','Android reconnect gate must remain OV13',failures)
    require(gm.get('automation_activity_authority') is True,'OV11 must claim automation/activity authority',failures)
    require(gm.get('automation_activity_protocol')=='dustui.automation.activity.v1','automation/activity protocol mismatch',failures)
    require('jobs' not in android and 'workflows' not in android,'unsupported Devtool jobs/workflows must not be reintroduced',failures)
    for rel in ['go/internal/automation/automation.go','go/internal/automation/automation_test.go','android/app/src/main/kotlin/com/mikeyphw/dustui/go/AutomationActivityContract.kt','go/internal/state/schema.go','go/internal/readiness/readiness.go','go/cmd/dustui-go/main.go']:
        require((ROOT/rel).is_file(),f'OV11 file missing: {rel}',failures)
    contract=run_json(['go','run','./cmd/dustui-go','automation-contract'])
    require(contract.get('protocol')=='dustui.automation.activity.v1','automation/activity protocol mismatch',failures)
    require(contract.get('phase')=='OV11','automation/activity phase must be OV11',failures)
    require(contract.get('goAuthority') is True,'automation/activity must claim Go authority',failures)
    require(contract.get('acceptedIsSuccess') is False,'accepted must not be success',failures)
    require(contract.get('queuedIsSuccess') is False,'queued must not be success',failures)
    require(contract.get('runningIsSuccess') is False,'running must not be success',failures)
    for adapter in ['ui','workmanager','tasker','notification','recovery']:
        require(adapter in contract.get('adapters',[]),f'adapter missing: {adapter}',failures)
    for op in ['scan','status','cancel','inspect']:
        require(op in contract.get('allowedAutomationOperations',[]),f'allowed automation op missing: {op}',failures)
    for guard in ['nonce','expiry','single_use','operation_binding','replay_rejection']:
        require(guard in contract.get('notificationGuards',[]),f'notification guard missing: {guard}',failures)
    for surface in ['active','history','run_details','progress','result','verification','recovery_required','retry_gate','cancel_gate','reconcile_gate','operation_evidence_correlation']:
        require(surface in contract.get('activitySurfaces',[]),f'Activity surface missing: {surface}',failures)
    for rule in ['accepted_not_success','queued_not_success','running_not_success','thin_adapters','process_death_does_not_orphan_job','cancellation_is_operation_transition','non_destructive_automation_only']:
        require(rule in contract.get('safetyRules',[]),f'automation safety rule missing: {rule}',failures)
    readiness=run_json(['go','run','./cmd/dustui-go','readiness'])
    require(readiness.get('migrationPhase')=='OV11','readiness phase must be OV11',failures)
    require(readiness.get('automationActivity') is True,'readiness must expose automation/activity',failures)
    require(readiness.get('productionEngine') is False,'Android production engine must remain suspended until OV13',failures)
    schema=run_json(['go','run','./cmd/dustui-go','schema'])
    require(schema.get('schemaVersion')==8,'OV11 schema version must be 8',failures)
    tables=schema.get('requiredTables',[])
    for table in ['automation_admissions','automation_sources','notification_actions','activity_runs']:
        require(table in tables,f'schema must include {table}',failures)
    joined='\n'.join(schema.get('createStatements',[]))
    for token in ['accepted_not_success','terminal_truth','evidence_correlation','thin_adapter','replay_rejected','operation_binding','retry_gate','cancel_gate','reconcile_gate']:
        require(token in joined,f'OV11 schema token missing: {token}',failures)
    for rel in ['migration/contracts/v1/automation_activity_ov11.md','migration/contracts/v1/authority_transition_ov11.md','migration/contracts/v1/legacy_paths_ov11.md','migration/contracts/v1/source_token_retirement_ov11.md','migration/audit/v1/ov11_audit_loop.md','migration/ledger/ov11_status.json']:
        require((ROOT/rel).is_file(),f'OV11 contract missing: {rel}',failures)
    findings=json.loads(read('migration/ledger/findings.json'))
    require(findings.get('phase')=='OV11','migration ledger phase must advance to OV11',failures)
    by_id={item.get('id'):item for item in findings.get('findings',[])}
    for fid in ['RC-06','RC-10','ABH-04','ABH-15','RM-08','GO13']:
        require(fid in by_id and 'OV11' in by_id[fid].get('overlays',[]),f'OV11 ledger mapping missing: {fid}',failures)
        require(by_id[fid].get('ov11_status'),f'OV11 ledger status missing: {fid}',failures)
    if audit_loop:
        combined='\n'.join(read(rel) for rel in ['migration/contracts/v1/automation_activity_ov11.md','migration/contracts/v1/authority_transition_ov11.md','migration/contracts/v1/legacy_paths_ov11.md','migration/contracts/v1/source_token_retirement_ov11.md','migration/audit/v1/ov11_audit_loop.md'])
        for phrase in ['accepted != success','queued != success','running != success','notification actions carry nonce','process death does not orphan','Activity exposes active/history/recovery','Automation remains non-destructive','Android buildability remains intentionally suspended']:
            require(phrase in combined,f'OV11 audit loop missing: {phrase}',failures)
    return failures

def main() -> int:
    parser=argparse.ArgumentParser(); parser.add_argument('--audit-loop',action='store_true'); args=parser.parse_args()
    failures=validate(args.audit_loop)
    if failures:
        for f in failures: print(f'OV11 contract failure: {f}',file=sys.stderr)
        return 1
    print('DustUI OV11 automation/activity contract passed')
    return 0
if __name__=='__main__': raise SystemExit(main())
