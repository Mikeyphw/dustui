#!/usr/bin/env python3
"""AUD-FIX successor-aware OV14 contract validator.

This validator proves truth-model consistency. It does not require predecessor
symbols and it does not make OV14 retirement a prerequisite for Gate D.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def req(ok: bool, msg: str, failures: list[str]) -> None:
    if not ok: failures.append(msg)


def main() -> int:
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    failures: list[str]=[]
    for rel in [
        'migration/qualification/v1/current_state.json',
        'migration/qualification/v1/gate_d_evidence.json',
        'migration/requirements/v2/requirements.json',
        'migration/ledger/ov14_legacy_retirement_status.json',
        'scripts/validate_ov14_retirement_truth_hotfix.py',
    ]:
        req((ROOT/rel).is_file(), f'missing {rel}', failures)
    if failures:
        for f in failures: print('OV14 contract failure:',f,file=sys.stderr)
        return 1

    state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
    ledger=json.loads((ROOT/'migration/ledger/ov14_legacy_retirement_status.json').read_text())
    cfg=tomllib.loads((ROOT/'.devtool.toml').read_text())
    android=cfg.get('targets',{}).get('dustui_android',{})
    workflows=android.get('workflows',{}); jobs=android.get('jobs',{})
    req('qualification_state' in jobs,'canonical qualification job missing',failures)
    req(workflows.get('gate_d',[None])[0]=='job:qualification_state','Gate D must start from canonical qualification state, not OV14 retirement',failures)
    req('job:retirement_gate' not in workflows.get('gate_d',[]),'Gate D must not depend on post-Gate-D OV14 retirement',failures)
    req(workflows.get('cutover_audit',[None])[0]=='job:qualification_state','cutover audit must begin with canonical qualification state',failures)
    req(ledger.get('qualification_state_source')=='migration/qualification/v1/current_state.json','OV14 ledger not bound to canonical state',failures)
    req('gate_d_truth' not in ledger and 'ov15_status' not in ledger,'OV14 ledger independently declares Gate D/OV15 state',failures)
    if state.get('gate_d',{}).get('status')=='open':
        req(ledger.get('status')=='blocked_on_gate_d','OV14 must remain blocked while canonical Gate D is open',failures)

    if args.audit_loop:
        for argv in [
            ['python3','scripts/refresh_qualification_state.py','--check'],
            ['python3','scripts/refresh_ov14_retirement_observation.py','--check'],
            ['python3','scripts/validate_ov14_retirement_truth_hotfix.py'],
        ]:
            p=subprocess.run(argv,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
            req(p.returncode==0, f"{' '.join(argv)} failed: {p.stdout.strip()}", failures)

    if failures:
        for f in failures: print('OV14 contract failure:',f,file=sys.stderr)
        return 1
    print(f"OV14 successor-aware contract PASS (Gate D={state['gate_d']['status']}; OV14={ledger['status']})")
    return 0

if __name__=='__main__': raise SystemExit(main())
