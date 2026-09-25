#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, sys, tomllib
from pathlib import Path
from audfix13_acceptance import ROOT, lifecycle_failures, ui_matrix_failures, metadata_failures, docs_failures, compute_state, evaluate_counterexample


def section_failures(section: str) -> list[str]:
    if section=='inventory': return lifecycle_failures(ROOT)
    if section=='validators': return lifecycle_failures(ROOT)+docs_failures(ROOT)
    if section=='metadata': return metadata_failures(ROOT)
    if section=='conformance': return lifecycle_failures(ROOT)+ui_matrix_failures(ROOT)+metadata_failures(ROOT)+docs_failures(ROOT)
    if section=='counterexamples':
        fixture=json.loads((ROOT/'migration/acceptance/v1/fixtures/counterexamples.json').read_text())
        failures=[]
        for case in fixture.get('cases',[]):
            detected=set(evaluate_counterexample(case))
            expected=set(case.get('expected_failures',[]))
            if detected!=expected: failures.append(f"counterexample {case.get('id')} detected={sorted(detected)} expected={sorted(expected)}")
        return failures
    return [f'unknown section {section}']


def check_devtool_binding() -> list[str]:
    f=[]; cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
    android=cfg['targets']['dustui_android']; jobs=android.get('jobs',{}); flows=android.get('workflows',{})
    for job in ('acceptance_inventory','successor_validator_contract','evidence_metadata_truth','pre_gate_d_conformance'):
        if job not in jobs: f.append(f'missing Devtool job {job}')
        elif jobs[job].get('metadata',{}).get('read_only') is not True: f.append(f'{job} must be read-only')
    flow=flows.get('aud_fix_13')
    if not isinstance(flow,list): f.append('aud_fix_13 workflow missing')
    else:
        refs={node.get('ref') for node in flow if isinstance(node,dict)}
        for ref in ('job:acceptance_inventory','job:successor_validator_contract','job:evidence_metadata_truth','job:pre_gate_d_conformance'):
            if ref not in refs: f.append(f'aud_fix_13 missing {ref}')
    wrapper=cfg.get('wrapper',{}); commands=wrapper.get('commands',{})
    if wrapper.get('default_command')!='aud-fix-13': f.append('wrapper default must advance to aud-fix-13')
    cmd=commands.get('aud-fix-13',{})
    if cmd.get('workflow')!='aud_fix_13' or cmd.get('target')!='dustui_android': f.append('aud-fix-13 wrapper binding invalid')
    return f


def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--section',choices=['inventory','validators','metadata','conformance','counterexamples','all'],default='all'); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    passes=2 if args.audit_loop else 1
    sections=['inventory','validators','metadata','conformance','counterexamples'] if args.section=='all' else [args.section]
    for run in range(1,passes+1):
        failures=[]
        for section in sections: failures += [f'{section}: {x}' for x in section_failures(section)]
        if args.section in ('all','conformance'): failures += [f'devtool: {x}' for x in check_devtool_binding()]
        state=compute_state(ROOT)
        if state['qualification']['gate_d_qualified'] is not False: failures.append('Gate D must remain open')
        if state['candidate']['acceptance_converged'] is not True: failures.append('candidate acceptance did not converge')
        print(f'AUD-FIX-13 {args.section} pass {run}: failures={len(failures)}')
        for x in failures: print('  - '+x)
        if failures: return 1
    print('converged=true')
    return 0
if __name__=='__main__': raise SystemExit(main())
