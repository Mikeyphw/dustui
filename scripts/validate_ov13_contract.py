#!/usr/bin/env python3
"""AUD-FIX successor-aware OV13/Gate-D contract validator."""
from __future__ import annotations
import argparse, json, subprocess, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def req(c,m,f):
    if not c: f.append(m)

def run_json(args):
    p=subprocess.run(args,cwd=ROOT/'go',text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if p.returncode!=0: raise RuntimeError(p.stderr or p.stdout)
    return json.loads(p.stdout)

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args(); failures=[]
    cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
    android=cfg.get('targets',{}).get('dustui_android',{}); go=cfg.get('targets',{}).get('dustui_go',{})
    am=android.get('metadata',{}); gm=go.get('metadata',{}); jobs=android.get('jobs',{}); flows=android.get('workflows',{})
    req(am.get('migration_phase')=='OV13','android migration phase must remain OV13',failures)
    req(gm.get('migration_phase')=='OV13','go migration phase must remain OV13',failures)
    req(am.get('android_buildability')=='mandatory','Android buildability must be mandatory at OV13',failures)
    req(am.get('qualification_state_source')=='migration/qualification/v1/current_state.json','Android metadata must consume canonical qualification state',failures)
    req(gm.get('qualification_state_source')=='migration/qualification/v1/current_state.json','Go metadata must consume canonical qualification state',failures)
    for banned in ['gate_d_truth','ov15_status','production_authority_qualified','android_cutover_complete','product_default','go_cli_default','go_tui_default','legacy_python_textual_production','legacy_kotlin_domain_truth']:
        req(banned not in am and banned not in gm,f'independent/aspirational metadata remains: {banned}',failures)
    req(gm.get('target_product_default') is True and gm.get('target_go_cli_default') is True and gm.get('target_go_tui_default') is True,'target product declarations missing',failures)
    req('qualification_state' in jobs,'qualification_state EXO job missing',failures)
    req(flows.get('gate_d',[None])[0]=='job:qualification_state','Gate D must begin with canonical qualification check',failures)
    req('job:retirement_gate' not in flows.get('gate_d',[]),'Gate D must not be sequenced after OV14 retirement',failures)
    req(flows.get('cutover_audit',[None])[0]=='job:qualification_state','cutover audit must begin with qualification check',failures)
    try:
        state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
        product_src=(ROOT/'go/internal/product/product.go').read_text()
        readiness_src=(ROOT/'go/internal/readiness/readiness.go').read_text()
        shell_src=(ROOT/'go/internal/shell/routes.go').read_text()
        generated_src=(ROOT/'go/internal/qualification/state_generated.go').read_text()
    except Exception as exc:
        failures.append(f'cannot load canonical static truth: {exc}'); state={'gate_d':{'qualified':False,'status':'unknown'}}; product_src=readiness_src=shell_src=generated_src=''
    qualified=state.get('gate_d',{}).get('qualified') is True
    literal='true' if qualified else 'false'
    req(f'const GateDQualified = {literal}' in generated_src,'generated Gate D constant diverges from canonical state',failures)
    req('ProductionEngine:              qualification.GateDQualified' in product_src,'product default must be evidence-derived',failures)
    req('GoSQLiteProductDefault:        qualification.GateDQualified' in product_src,'Go SQLite default must be evidence-derived',failures)
    req('DeclarationScope:           "canonical-evidence-derived:" + qualification.StateSource' in product_src,'product declaration scope not canonical',failures)
    req('ProductionEngine:        qualification.GateDQualified' in readiness_src,'readiness product truth must be canonical',failures)
    req('GoCLIDefault:            qualification.GateDQualified' in readiness_src and 'GoTUIDefault:            qualification.GateDQualified' in readiness_src,'CLI/TUI defaults must be evidence-derived',failures)
    for route in ['overview','files','clean','apps','activity','system']:
        req(f'RouteID = "{route}"' in shell_src,f'target semantic route missing: {route}',failures)
    required=set(state.get('gate_d',{}).get('required_nodes',[]))
    req('RequiredEvidence:           append([]string{}, qualification.RequiredEvidenceNodes...)' in product_src,'product required evidence must derive from canonical Gate D topology',failures)
    kt=(ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui/go/ProductCutoverContract.kt').read_text()
    req('QualificationStateSource' in kt and 'QualificationEvidenceSource' in kt,'Android target contract must point to canonical qualification sources',failures)
    req('LegacyPythonTextualProduction' not in kt and 'LegacyKotlinDomainTruth' not in kt,'Android target contract must not declare observed legacy absence',failures)
    if args.audit_loop:
        for cmd in [['python3','scripts/refresh_qualification_state.py','--check'],['python3','scripts/validate_ov14_contract.py']]:
            p=subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            req(p.returncode==0,f"{' '.join(cmd)} failed: {p.stdout.strip()}",failures)
    if failures:
        for x in failures: print('OV13 contract failure:',x,file=sys.stderr)
        return 1
    print(f"DustUI OV13 successor-aware Gate D contract PASS (canonical status={state['gate_d']['status']})")
    return 0
if __name__=='__main__': raise SystemExit(main())
