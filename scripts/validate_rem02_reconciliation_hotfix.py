#!/usr/bin/env python3
"""AUD-FIX successor-aware REM-02 reconciliation validator."""
from __future__ import annotations
import json, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
fail=[]
for validator in [
    'scripts/validate_rem01_android_go_runtime_binding.py',
    'scripts/validate_rem02_sqlite_integrity_hotfix.py',
    'scripts/validate_rem02_sqlite_persistence_authority.py',
    'scripts/validate_ov14_retirement_truth_hotfix.py',
]:
    p=subprocess.run([sys.executable,validator],cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode: fail.append(f'{validator} failed: {p.stdout.strip()}')
try:
    state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
    ledger=json.loads((ROOT/'migration/ledger/ov14_legacy_retirement_status.json').read_text())
    if ledger.get('qualification_state_source')!='migration/qualification/v1/current_state.json':
        fail.append('retirement ledger not bound to canonical qualification state')
    if 'gate_d_truth' in ledger or 'ov15_status' in ledger:
        fail.append('retirement ledger still declares Gate D/OV15 independently')
    if state.get('gate_d',{}).get('status') not in {'open','qualified'}:
        fail.append('invalid canonical Gate D status')
    if ledger.get('gate_d_observed_status')!=state.get('gate_d',{}).get('status'):
        fail.append('retirement ledger Gate-D observation is stale')
    if ledger.get('observed',{}).get('go_sqlite_persistence')!='go-owned-sqlite':
        fail.append('Go SQLite persistence implementation observation regressed')
except Exception as exc:
    fail.append(f'invalid canonical state/OV14 ledger: {exc}')
if fail:
    for item in fail: print('REM-02 reconciliation failure:',item,file=sys.stderr)
    raise SystemExit(1)
print('REM-02 successor-aware reconciliation PASS')
