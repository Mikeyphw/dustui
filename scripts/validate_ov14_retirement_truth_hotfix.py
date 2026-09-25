#!/usr/bin/env python3
"""AUD-FIX successor-aware OV14 retirement truth audit.

Default mode validates that the OV14 observation ledger matches current source
reachability and canonical qualification state. `--gate` is the *OV14 physical
retirement* assertion and therefore additionally requires canonical Gate D
qualification plus zero retirement blockers. It never closes Gate D itself.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from audfix01_core import DESIRED_RETIREMENT, load_json, retirement_observation

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'migration/qualification/v1/current_state.json'
LEDGER = ROOT / 'migration/ledger/ov14_legacy_retirement_status.json'


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--gate', action='store_true')
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args()
    failures: list[str] = []

    refresh = subprocess.run(['python3', 'scripts/refresh_qualification_state.py', '--check'], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if refresh.returncode != 0:
        failures.append('canonical qualification state is stale: ' + refresh.stdout.strip())
    ledger_refresh = subprocess.run(['python3', 'scripts/refresh_ov14_retirement_observation.py', '--check'], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if ledger_refresh.returncode != 0:
        failures.append('OV14 retirement observation ledger is stale: ' + ledger_refresh.stdout.strip())

    state = load_json(STATE)
    ledger = load_json(LEDGER)
    observed, blockers = retirement_observation(ROOT, state)
    if ledger.get('qualification_state_source') != 'migration/qualification/v1/current_state.json':
        failures.append('OV14 ledger must consume canonical qualification state')
    if ledger.get('desired') != DESIRED_RETIREMENT:
        failures.append('OV14 desired retirement state mismatch')
    if ledger.get('observed') != observed or sorted(ledger.get('blockers', [])) != blockers:
        failures.append('OV14 retirement ledger does not match successor-aware reachability observation')
    if 'gate_d_truth' in ledger or 'ov15_status' in ledger:
        failures.append('OV14 ledger must not independently declare Gate D/OV15 state')

    gate_d = state['gate_d']['status']
    expected_status = 'blocked_on_gate_d' if gate_d != 'qualified' else ('ready_for_retirement_work' if blockers else 'retirement_conditions_observed')
    if ledger.get('status') != expected_status:
        failures.append(f'OV14 formal status must be {expected_status}')

    payload = {
        'schema': 'dustui.ov14-retirement-validator.v2',
        'gate_d': gate_d,
        'ov14_status': ledger.get('status'),
        'blockers': blockers,
        'failures': failures,
    }
    if args.json:
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        for f in failures:
            print('OV14 retirement truth failure: ' + f, file=sys.stderr)
    if failures:
        return 1
    if args.gate and (gate_d != 'qualified' or blockers):
        if not args.json:
            print('OV14 physical-retirement gate BLOCKED: Gate D must qualify first and all retirement blockers must be removed.', file=sys.stderr)
        return 1
    if not args.json:
        print(f'OV14 retirement truth audit PASS (Gate D={gate_d}; {len(blockers)} physical-retirement blockers)')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
