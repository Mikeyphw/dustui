#!/usr/bin/env python3
"""AUD-FIX-11 Activity/Diagnostics product UX acceptance leaves."""
from __future__ import annotations
import argparse, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def read(rel: str) -> str:
    p=ROOT/rel
    if not p.is_file(): raise AssertionError(f"missing required file: {rel}")
    return p.read_text(encoding="utf-8")

def req(text: str, token: str, label: str) -> None:
    if token not in text: raise AssertionError(f"{label}: missing {token!r}")

def reject(text: str, token: str, label: str) -> None:
    if token in text: raise AssertionError(f"{label}: forbidden predecessor {token!r}")

def activity() -> None:
    app=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt')
    models=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/ActivityModels.kt')
    vm=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt')
    repo=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt')
    predecessor=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/CleaningIntelligenceScreens.kt')
    for token in ('ActivityStateFilter','ActivityFilter','statusLabel','needsAttention'):
        req(models, token, 'durable Activity presentation model')
    for token in ('GlobalActiveOperationSurface','Open evidence','Recovery required','run.canCancel','run.canRetry','run.canReconcile'):
        req(app, token, 'Activity product surface')
    for token in ('repository.activityEvidence(operationId)','selectedActivityEvidence'):
        req(vm, token, 'Activity durable evidence selection')
    req(repo, 'suspend fun activityEvidence(', 'Activity Go adapter')
    reject(predecessor, 'fun CleanupReportsScreen(', 'separate deletion-report destination')

def diagnostics() -> None:
    app=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt')
    models=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/DiagnosticsAuthorityModels.kt')
    vm=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt')
    contract=read('android/app/src/main/kotlin/com/mikeyphw/dustui/go/DiagnosticsEvidenceContract.kt')
    for token in ('Root causes and remediation','Evidence health','Show advanced diagnostics','Export support bundle'):
        req(app, token, 'System Diagnostics primary UX')
    for token in ('problem.guidance','problem.remediationActions','Open related Activity run'):
        req(app, token, 'ProblemGraph remediation UX')
    for token in ('durableDiagnosticEvents','repository.diagnosticEvents()'):
        req(vm, token, 'durable diagnostics evidence')
    for token in ('entityId','unhealthyEvidence','recoveryProblems'):
        req(models, token, 'diagnostics authority model')
    req(contract, 'canonicalRoute: String = "system/diagnostics"', 'successor diagnostics route')
    req(contract, 'authority: String = "go-problem-graph"', 'successor diagnostics authority')
    reject(contract, 'intentionally-suspended', 'retired OV12 scaffold')
    reject(contract, 'System/Diagnostics', 'legacy diagnostics route spelling')

def navigation() -> None:
    go_state=read('go/internal/state/activity_product.go')
    go_diag=read('go/internal/state/diagnostics_authority.go')
    go_domain=read('go/internal/state/domain_authority.go')
    runtime=read('go/cmd/dustui-go/runtime_domain.go')
    kotlin_test=read('android/app/src/test/kotlin/com/mikeyphw/dustui/data/ActivityDiagnosticsProductUxTest.kt')
    go_test=read('go/internal/state/audfix11_activity_diagnostics_test.go')
    for token in ('ActivityEvidenceByID','CleanupVerification','Problems'):
        req(go_state, token, 'Go correlated Activity evidence')
    for token in ('OPEN_ACTIVITY_RUN','"activity/run"','EntityID: recoveryOperationID'):
        req(go_diag, token, 'diagnostics-to-Activity correlation')
    req(go_domain, 'UPDATE activity_runs', 'verification-to-Activity evidence persistence')
    req(runtime, 'domain.automation.activity.evidence', 'Activity evidence runtime operation')
    req(kotlin_test, 'AUD FIX11 Activity run deep link round trips operation correlation', 'Android correlation test')
    req(go_test, 'TestAUD_FIX11CleanupVerificationIsActivityEvidence', 'Go cleanup evidence test')
    req(go_test, 'TestAUD_FIX11DiagnosticsRemediationTargetsExactRecoveryOperation', 'Go diagnostics correlation test')

def binding() -> None:
    dev=read('.devtool.toml')
    cfg=tomllib.loads(dev)
    android=cfg['targets']['dustui_android']
    go=cfg['targets']['dustui_go']
    for name in ('activity_ui_contract','diagnostics_ui_contract','operation_evidence_navigation'):
        if name not in android['jobs']: raise AssertionError(f'Devtool Android leaf missing: {name}')
    if 'audfix11_activity_diagnostics' not in go['jobs']:
        raise AssertionError('Devtool Go AUD-FIX-11 leaf missing')
    if 'aud_fix_11' not in android['workflows'] or 'aud_fix_11' not in go['workflows']:
        raise AssertionError('Devtool AUD-FIX-11 workflows missing')
    req(dev, '[wrapper.commands.aud-fix-11]', 'Devtool wrapper binding')

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--section', choices=['activity','diagnostics','navigation','binding','all'], default='all')
    args=ap.parse_args()
    checks={'activity':activity,'diagnostics':diagnostics,'navigation':navigation,'binding':binding}
    selected=checks if args.section=='all' else {args.section: checks[args.section]}
    for name, fn in selected.items():
        fn(); print(f'AUD-FIX-11 {name} contract: PASS')
    return 0

if __name__=='__main__':
    try: raise SystemExit(main())
    except AssertionError as exc:
        print(f'AUD-FIX-11 contract: FAIL: {exc}', file=sys.stderr); raise SystemExit(1)
