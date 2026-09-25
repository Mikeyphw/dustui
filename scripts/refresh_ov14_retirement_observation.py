#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from audfix01_core import DESIRED_RETIREMENT, load_json, retirement_observation

ROOT = Path(__file__).resolve().parents[1]
STATE_PATH = ROOT / 'migration/qualification/v1/current_state.json'
OUT = ROOT / 'migration/ledger/ov14_legacy_retirement_status.json'


def render() -> dict:
    state = load_json(STATE_PATH)
    observed, blockers = retirement_observation(ROOT, state)
    gate_d = state['gate_d']['status']
    formal_status = 'blocked_on_gate_d' if gate_d != 'qualified' else ('ready_for_retirement_work' if blockers else 'retirement_conditions_observed')
    return {
        'schema': 'dustui.ov14-retirement-observation.v2',
        'phase': 'OV14',
        'status': formal_status,
        'qualification_state_source': 'migration/qualification/v1/current_state.json',
        'gate_d_observed_status': gate_d,
        'desired': DESIRED_RETIREMENT,
        'observed': observed,
        'blockers': blockers,
        'closure_rule': 'OV14 physical retirement may close only after canonical Gate D qualification and behavioral/reachability retirement proof; this ledger cannot close Gate D.',
        'next': 'AUD-FIX-02' if gate_d != 'qualified' else 'AUD-FIX-16',
    }


def main() -> int:
    ap=argparse.ArgumentParser(); ap.add_argument('--check',action='store_true'); ap.add_argument('--json',action='store_true'); args=ap.parse_args()
    obj=render(); txt=json.dumps(obj,indent=2,sort_keys=True)+'\n'
    if args.check:
        if not OUT.is_file() or OUT.read_text()!=txt:
            print('OV14 retirement observation ledger is stale')
            return 1
    else:
        OUT.write_text(txt)
    if args.json: print(txt,end='')
    else: print(f"OV14 {obj['status']} ({len(obj['blockers'])} retirement blockers; Gate D={obj['gate_d_observed_status']})")
    return 0

if __name__=='__main__': raise SystemExit(main())
