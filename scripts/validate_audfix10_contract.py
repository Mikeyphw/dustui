#!/usr/bin/env python3
"""AUD-FIX-10 scan/Overview/Apps UX truth contract."""
from __future__ import annotations
import re, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(rel):
    p=ROOT/rel
    if not p.is_file(): raise AssertionError(f"missing required file: {rel}")
    return p.read_text(encoding="utf-8")
def req(t,x,l):
    if x not in t: raise AssertionError(f"{l}: missing {x!r}")
def reject(t,x,l):
    if x in t: raise AssertionError(f"{l}: forbidden predecessor {x!r}")
def main():
    flow=read('android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanLaunchFlow.kt')
    vm=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt')
    app=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt')
    apps=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/AppStorageInspectorScreen.kt')
    snap=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/SnapshotModels.kt')
    product=read('go/internal/state/product_views_bounded.go')
    domain=read('go/internal/state/domain_authority.go')
    gates=read('go/internal/shell/async.go')
    gotest=read('go/internal/shell/async_test.go')
    dev=read('.devtool.toml')
    for x in ('ChooseScope','Preflight','Review','Starting','canStart','beginStart'):
        req(flow,x,'mandatory scan launch protocol')
    req(app,'Review this scope','scan review UI'); req(app,'vm.confirmReviewedScan()','explicit Start action')
    if vm.count('repository.startScan(request)') != 1:
        raise AssertionError('production scan execution must have exactly one repository.startScan(request) call')
    req(vm,'fun confirmReviewedScan()','reviewed scan authority')
    reject(app,'vm.startPreset(','one-tap preset bypass')
    for x in ('Logical bytes','Allocated bytes','Recent activity','Review evidence'):
        req(app,x,'Overview truth surface')
    for x in ('scanId','generation','integrityStatus','logicalBytes','allocatedBytes','fileCount'):
        req(snap,x,'Overview semantic model')
    for x in ('"logicalBytes"','"allocatedBytes"','"overview"'):
        req(product+domain,x,'Go Overview authority')
    for x in ('AppStorageEvidenceState.PARTIAL','AppStorageEvidenceState.UNAVAILABLE','AppStorageEvidenceState.BLOCKED','Unknown /','never rendered as a zero-valued bucket','measured'):
        req(apps,x,'Apps evidence completeness')
    for x in ('root_unavailable','preflight_failed','wrong_scope','integrity_degraded','evidence_unavailable'):
        req(gates,x,'typed Go remediation gate')
    req(gotest,'AUD_FIX10','Go AUD-FIX-10 gate coverage')
    cfg=tomllib.loads(dev)
    if 'aud_fix_10' not in cfg['targets']['dustui_android']['workflows'] or 'aud_fix_10' not in cfg['targets']['dustui_go']['workflows']:
        raise AssertionError('Devtool AUD-FIX-10 workflows missing')
    for x in ('audfix10_ux_truth_contract','audfix10_ux_truth','[wrapper.commands.aud-fix-10]'):
        req(dev,x,'Devtool AUD-FIX-10 binding')
    print('AUD-FIX-10 scan/Overview/Apps UX truth contract: PASS')
    return 0
if __name__=='__main__':
    try: raise SystemExit(main())
    except AssertionError as e:
        print(f'AUD-FIX-10 contract: FAIL: {e}',file=sys.stderr); raise SystemExit(1)
