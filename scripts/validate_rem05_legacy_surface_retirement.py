#!/usr/bin/env python3
"""AUD-FIX successor-aware REM-05 retirement validator.

Default mode is a truth audit and passes when the ledger accurately reports an
open/blocked retirement state. `--gate` is the actual physical-retirement seal.
"""
from __future__ import annotations
import argparse, json, subprocess, sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--gate',action='store_true'); args=ap.parse_args()
    p=subprocess.run(['python3','scripts/validate_ov14_retirement_truth_hotfix.py'] + (['--gate'] if args.gate else []),cwd=R,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode!=0:
        print('REM-05 retirement validation blocked/fail:',p.stdout.strip(),file=sys.stderr); return 1
    state=json.loads((R/'migration/qualification/v1/current_state.json').read_text())
    ledger=json.loads((R/'migration/ledger/ov14_legacy_retirement_status.json').read_text())
    print(f"REM-05 successor-aware retirement truth PASS (Gate D={state['gate_d']['status']}; OV14={ledger['status']}; blockers={len(ledger['blockers'])})")
    return 0
if __name__=='__main__': raise SystemExit(main())
