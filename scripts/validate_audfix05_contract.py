#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re, tomllib
from pathlib import Path

from validate_audfix01_contract import gate_d_phase_failures
ROOT=Path(__file__).resolve().parents[1]
def text(rel):
    p=ROOT/rel; return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''
def check_source():
    f=[]
    def req(ok,code,detail):
        if not ok: f.append({'code':code,'detail':detail})
    diag=text('go/internal/diagnostics/diagnostics.go')
    store=text('go/internal/state/diagnostics_authority.go')
    runtime=text('go/cmd/dustui-go/runtime_domain.go')
    auto=text('go/internal/state/automation_authority.go')
    repo=text('android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt')
    writer=text('android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt')
    vm=text('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt')
    cfg=text('.devtool.toml')
    for token in ['MaxEventsPerSession','MaxSessions','MaxSupportBundles','DiagnosticRetainDays','SupportBundleRetainDays','RemediationActions','ExpiresAtMS','DurableSupportOperation']:
        req(token in diag,'AUD013_DIAGNOSTICS_CONTRACT',f'diagnostics contract missing {token}')
    for token in ['RedactEvent','PruneDiagnostics','problem_graph_nodes','significant_problem_records','privacy_retention_rules','support_bundles','DiagnosticsSnapshot','PersistSupportBundle','latestRecoveryOperationID']:
        req(token in store,'AUD013_DURABLE_STATE',f'durable diagnostic authority missing {token}')
    for token in ['domain.diagnostics.record','domain.diagnostics.list','domain.diagnostics.snapshot','domain.diagnostics.compact','domain.support.bundle','domain.support.bundle.delete','support_bundle_privacy_mode_invalid']:
        req(token in runtime,'AUD013_RUNTIME_REACHABILITY',f'Go runtime operation missing {token}')
    req('"support-export"' in auto,'AUD013_SUPPORT_OPERATION','support export is not a durable automation kind')
    req('domain.support.bundle' in repo and 'diagnosticsSnapshot' in repo and 'recordDiagnosticEvent' in repo,'AUD013_ANDROID_GO_AUTHORITY','Android does not consume Go diagnostics/support authority')
    req('writeSealed' in writer and 'go-domain-support-bundle' in writer and 'trace-events.jsonl' not in writer,'AUD013_PACKAGING_ONLY','Android support writer still authors/selects raw diagnostic evidence')
    req('repository.recordDiagnosticEvent' in vm and 'diagnosticsAuthority' in vm,'AUD013_VM_AUTHORITY','MainViewModel is not connected to Go durable diagnostics')
    # Devtool-native validation architecture: checker is a leaf and jobs/workflows are authoritative.
    for job in ['audfix01_truth','audfix02_bounded','audfix03_domain','audfix04_operations','audfix05_android_contract','android_unit','android_lint','android_assemble']:
        req(f'[targets.dustui_android.jobs.{job}]' in cfg,'AUD021_DEVTOOL_JOB',f'Devtool job missing {job}')
    for wf in ['aud_fix_regression','aud_fix_05','authority_gate_r1','validate']:
        req(re.search(rf'^{re.escape(wf)}\s*=\s*\[',cfg,re.M) is not None,'AUD021_DEVTOOL_WORKFLOW',f'Devtool workflow missing {wf}')
    req('target:dustui_go#authority_gate_r1' in cfg,'AUD021_CROSS_TARGET','Authority gate does not compose the Go target DAG')
    req('[targets.dustui_go.jobs.audfix05_diagnostics_go]' in cfg,'AUD021_DEVTOOL_JOB','Go AUD-FIX-05 diagnostics job missing')
    req('validation.tasks' not in cfg,'AUD021_EXCLUSIVE_TASK_OVERRIDE','Devtool config must not use validation.tasks for the multi-runner R1 gate')
    # Gate R1 has passed and the temporary Gradle pin is lifted. Keep the leaf
    # jobs in the canonical R1/full-validation graph so later stages cannot
    # silently lose Android buildability coverage.
    try:
        parsed_cfg=tomllib.loads(cfg)
        workflows=parsed_cfg.get('targets',{}).get('dustui_android',{}).get('workflows',{})
    except Exception as exc:
        workflows={}; req(False,'AUD021_DEVTOOL_TOML',f'cannot parse .devtool.toml: {exc}')
    for wf in ['authority_gate_r1','validate']:
        nodes=workflows.get(wf,[])
        refs={node.get('ref') if isinstance(node,dict) else node for node in nodes}
        req(bool(nodes),'AUD021_DEVTOOL_WORKFLOW',f'Devtool workflow body missing {wf}')
        for job in ['gradle_contract','android_unit','android_lint','android_assemble']:
            req(f'job:{job}' in refs,'AUD021_GRADLE_COVERAGE',f'{wf} must schedule restored Gradle job {job}')
    # Custom leaf validators may not orchestrate builders/device/devtool.
    for n in range(1,6):
        src=text(f'scripts/validate_audfix0{n}_contract.py')
        req(re.search(r'\bimport\s+subprocess\b|\bsubprocess\.', src) is None,'AUD021_NO_CUSTOM_ORCHESTRATION',f'AUD-FIX-0{n} checker still launches subprocesses')
    # AUD-FIX-03/04/05 successor replay uses canonical AUD-FIX-01 phase truth.
    q=ROOT/'migration/qualification/v1/current_state.json'
    if q.is_file():
        obj=json.loads(q.read_text())
        phase_failures=gate_d_phase_failures(obj)
        req(not phase_failures,'ROADMAP_GATE_D_SUCCESSOR_STATE',
            'AUD-FIX-05 Gate-D/Gate-E successor phase is invalid: '+
            ', '.join(x.get('code','unknown') for x in phase_failures))
    return f
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    passes=2 if args.audit_loop else 1
    for i in range(1,passes+1):
        f=check_source(); print(f'AUD-FIX-05 leaf pass {i}: failures={len(f)}')
        for x in f: print(f"  - {x['code']}: {x['detail']}")
        if f: return 1
    print('converged=true'); return 0
if __name__=='__main__': raise SystemExit(main())
