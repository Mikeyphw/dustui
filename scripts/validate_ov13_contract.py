#!/usr/bin/env python3
"""AUD-FIX-13 successor-aware OV13/Gate-D static contract validator.

Leaf-only: it consumes generated acceptance/qualification evidence and Devtool
configuration. It never launches Go, Gradle, another validator, or a device.
"""
from __future__ import annotations
import argparse, json, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def validate()->list[str]:
    failures=[]
    cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
    targets=cfg.get('targets',{}); android=targets.get('dustui_android',{}); go=targets.get('dustui_go',{})
    am=android.get('metadata',{}); gm=go.get('metadata',{}); jobs=android.get('jobs',{}); flows=android.get('workflows',{})
    state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
    acceptance=json.loads((ROOT/'migration/acceptance/v1/current_state.json').read_text())
    def req(ok,msg):
        if not ok: failures.append(msg)
    req(am.get('migration_phase')=='OV13' and gm.get('migration_phase')=='OV13','migration phase must remain OV13')
    req(am.get('android_buildability')=='mandatory','Android buildability must remain mandatory')
    for md,name in ((am,'Android'),(gm,'Go')):
        req(md.get('qualification_state_source')=='migration/qualification/v1/current_state.json',f'{name} metadata must consume qualification state')
        req(md.get('acceptance_state_source')=='migration/acceptance/v1/current_state.json',f'{name} metadata must consume acceptance state')
        for banned in ('gate_d_truth','ov15_status','production_authority_qualified','android_cutover_complete'):
            req(banned not in md,f'{name} metadata has independent qualified claim {banned}')
    req(gm.get('target_product_default') is True and gm.get('target_product_default_scope')=='host-cli-tui-only','Go product-default claim must be explicitly host-scoped')
    req(gm.get('host_authority_default') is True and gm.get('legacy_python_textual_production') is False,'Go host identity declarations missing')
    req(state.get('gate_d',{}).get('status')=='open' and state.get('gate_d',{}).get('qualified') is False,'Gate D must remain open before AUD-FIX-15')
    req(acceptance.get('candidate',{}).get('acceptance_converged') is True,'candidate acceptance must converge before Gate-D topology work')
    req(acceptance.get('qualification',{}).get('gate_d_qualified') is False,'acceptance state must not imply Gate-D qualification')
    for job in ('acceptance_inventory','successor_validator_contract','evidence_metadata_truth','pre_gate_d_conformance','qualification_state'):
        req(job in jobs,f'missing current acceptance job {job}')
    req('aud_fix_13' in flows,'aud_fix_13 workflow missing')
    serialized=json.dumps({'aud_fix_13':flows.get('aud_fix_13'),'gate_d':flows.get('gate_d')})
    for path in acceptance.get('candidate',{}).get('quarantined_generic_helpers',[]):
        req(path not in serialized and Path(path).name not in serialized,f'quarantined helper referenced by Devtool workflow: {path}')
    product=(ROOT/'go/internal/product/product.go').read_text()
    req('AcceptanceStateSource:' in product and 'qualification.AcceptanceStateSource' in product,'Go product contract must consume generated acceptance state')
    req('qualification.GateDQualified' in product and 'GateDQualified:' in product,'Android default must remain Gate-D evidence controlled')
    return failures

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    for run in range(1,3 if args.audit_loop else 2):
        failures=validate(); print(f'OV13 successor contract pass {run}: failures={len(failures)}')
        for x in failures: print('OV13 contract failure: '+x,file=sys.stderr)
        if failures: return 1
    print('DustUI OV13 successor-aware pre-Gate-D contract PASS')
    return 0
if __name__=='__main__': raise SystemExit(main())
