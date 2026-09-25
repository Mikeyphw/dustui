#!/usr/bin/env python3
from __future__ import annotations
import argparse, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def req(f, ok, code, detail):
    if not ok: f.append({"code":code,"detail":detail})
def check_source():
    f=[]
    state_p=ROOT/'migration/qualification/v1/current_state.json'
    req(f,state_p.is_file(),'AUD001_STATE','canonical qualification state missing')
    if state_p.is_file():
        state=json.loads(state_p.read_text())
        req(f,state.get('gate_d',{}).get('qualified') is False,'AUD001_GATE_D','Gate D must remain open before AUD-FIX-15')
    idx=ROOT/'migration/requirements/v2/requirements.json'
    req(f,idx.is_file(),'AUD001_REQUIREMENTS','requirement index missing')
    if idx.is_file():
        obj=json.loads(idx.read_text()); raw=json.dumps(obj)
        for fam in ['AUD-001','AUD-002','AUD-003','AUD-004','AUD-005','UIX-001','UIX-009']:
            req(f,fam in raw,'AUD001_REQUIREMENT_FAMILY',f'requirement index missing {fam}')
    for rel in ['scripts/refresh_qualification_state.py','scripts/validate_full_roadmap_conformance.py','migration/qualification/v1/gate_d_evidence.json']:
        req(f,(ROOT/rel).is_file(),'AUD001_CANONICAL_FILE',f'canonical truth file missing {rel}')
    return f
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); args=ap.parse_args()
    passes=2 if args.audit_loop else 1; previous=None
    for i in range(1,passes+1):
        f=check_source(); print(f'AUD-FIX-01 leaf pass {i}: failures={len(f)}')
        for x in f: print(f"  - {x['code']}: {x['detail']}")
        if f: return 1
        previous=tuple()
    print('converged=true')
    return 0
if __name__=='__main__': raise SystemExit(main())
