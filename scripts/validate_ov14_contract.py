#!/usr/bin/env python3
"""Successor-aware OV14 milestone truth validator; static/evidence-only."""
from __future__ import annotations
import argparse, json, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def validate()->list[str]:
    failures=[]
    def req(ok,msg):
        if not ok: failures.append(msg)
    state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
    acceptance=json.loads((ROOT/'migration/acceptance/v1/current_state.json').read_text())
    ledger=json.loads((ROOT/'migration/ledger/ov14_legacy_retirement_status.json').read_text())
    cfg=tomllib.loads((ROOT/'.devtool.toml').read_text()); flows=cfg['targets']['dustui_android'].get('workflows',{})
    req(state.get('gate_d',{}).get('status')=='open','OV14 predecessor retirement must not precede Gate D')
    req(ledger.get('qualification_state_source')=='migration/qualification/v1/current_state.json','OV14 ledger not bound to canonical qualification state')
    req('gate_d_truth' not in ledger and 'ov15_status' not in ledger,'OV14 ledger independently declares gate truth')
    req(ledger.get('status')=='blocked_on_gate_d','OV14 must remain blocked while Gate D is open')
    req(acceptance.get('candidate',{}).get('acceptance_converged') is True,'pre-Gate-D acceptance must converge before OV14 topology/requalification work')
    req('job:retirement_gate' not in json.dumps(flows.get('gate_d')),'Gate D must not depend on post-Gate-D physical retirement')
    return failures

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    passes=2 if args.audit_loop else 1
    for run in range(1,passes+1):
        f=validate(); print(f'OV14 successor contract pass {run}: failures={len(f)}')
        for x in f: print('OV14 contract failure: '+x,file=sys.stderr)
        if f:return 1
    print('OV14 successor-aware milestone truth PASS')
    return 0
if __name__=='__main__': raise SystemExit(main())
