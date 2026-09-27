#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def req(f, ok, code, detail):
    if not ok: f.append({"code":code,"detail":detail})
def gate_d_phase_failures(state, root=ROOT):
    """Validate Gate-D truth in both its historical and successor phases."""
    f=[]
    gate_d=state.get('gate_d',{}) if isinstance(state,dict) else {}
    qualified=gate_d.get('qualified')
    status=str(gate_d.get('status') or '')

    # Historical invariant: until AUD-FIX-15 legitimately qualifies Gate D,
    # AUD-FIX-01 must keep it open. This remains intentionally strict.
    if qualified is False:
        req(f,status=='open','AUD001_GATE_D','Gate D must remain open before AUD-FIX-15')
        return f

    # Successor invariant: once Gate D is qualified, do not demand that it be
    # reopened. Require the qualified ancestor and its canonical source-bound
    # evidence to remain preserved while Gate E becomes the current successor.
    req(f,qualified is True,'AUD001_GATE_D_STATE','Gate D qualified flag must be boolean')
    if qualified is not True:
        return f
    req(f,status=='qualified','AUD001_GATE_D_SUCCESSOR','qualified Gate D must retain qualified status')

    ancestor=str(gate_d.get('passing_source_tree_sha256') or '').strip()
    req(f,bool(re.fullmatch(r'[0-9a-fA-F]{64}',ancestor)),'AUD001_GATE_D_ANCESTOR','qualified Gate D must retain its canonical source ancestor')

    evidence_specs=(
        ('migration/qualification/v1/gate_d_evidence.json','AUD001_GATE_D_EVIDENCE','AUD001_GATE_D_EVIDENCE_ANCESTOR'),
        ('migration/qualification/v1/gate_d_bundle.json','AUD001_GATE_D_BUNDLE','AUD001_GATE_D_BUNDLE_ANCESTOR'),
    )
    for rel,missing_code,binding_code in evidence_specs:
        p=root/rel
        req(f,p.is_file(),missing_code,f'qualified Gate D evidence missing {rel}')
        if not p.is_file():
            continue
        try:
            obj=json.loads(p.read_text())
        except (OSError,json.JSONDecodeError) as exc:
            req(f,False,missing_code,f'qualified Gate D evidence unreadable {rel}: {exc}')
            continue
        req(
            f,
            bool(ancestor) and str(obj.get('source_snapshot_sha256') or '')==ancestor,
            binding_code,
            f'qualified Gate D evidence no longer preserves ancestor {rel}',
        )

    ov14=state.get('ov14',{}) if isinstance(state,dict) else {}
    gate_e=state.get('gate_e',{}) if isinstance(state,dict) else {}
    ov14_status=str(ov14.get('status') or '')
    gate_e_status=str(gate_e.get('status') or '')
    gate_e_qualified=gate_e.get('qualified')

    if ov14_status=='physically-retired':
        req(
            f,
            gate_e_status in {'eligible-for-qualification','qualified'},
            'AUD001_GATE_E_SUCCESSOR',
            'after physical retirement Gate E must be eligible/current or qualified',
        )
        if gate_e_status=='qualified':
            req(f,gate_e_qualified is True,'AUD001_GATE_E_STATE','qualified Gate E must set qualified=true')
        elif gate_e_status=='eligible-for-qualification':
            req(f,gate_e_qualified is False,'AUD001_GATE_E_STATE','eligible Gate E must remain unqualified until finalization')
    else:
        req(
            f,
            ov14_status in {'eligible-for-physical-retirement','retirement-evidence-pending'},
            'AUD001_GATE_D_SUCCESSOR_PHASE',
            'qualified Gate D must either await physical retirement or advance to Gate E',
        )
        req(
            f,
            gate_e_status=='blocked-on-ov14-retirement',
            'AUD001_GATE_E_SUCCESSOR',
            'before physical retirement Gate E must remain blocked on AUD-FIX-16',
        )
        req(f,gate_e_qualified is False,'AUD001_GATE_E_STATE','blocked Gate E must remain unqualified')
    return f

def check_source():
    f=[]
    state_p=ROOT/'migration/qualification/v1/current_state.json'
    req(f,state_p.is_file(),'AUD001_STATE','canonical qualification state missing')
    if state_p.is_file():
        state=json.loads(state_p.read_text())
        f.extend(gate_d_phase_failures(state))
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
