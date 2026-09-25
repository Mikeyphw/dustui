#!/usr/bin/env python3
"""AUD-FIX successor-aware. AUD-FIX-13 successor acceptance alias for historical Debug Workbench checks."""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main()->int:
    state=json.loads((ROOT/'migration/acceptance/v1/current_state.json').read_text())
    matrix=json.loads((ROOT/'migration/acceptance/v1/ui_acceptance_matrix.json').read_text())
    rows={r['requirement_id']:r for r in matrix['requirements']}
    failures=[]
    if not state.get('candidate',{}).get('acceptance_converged'): failures.append('candidate acceptance has not converged')
    for rid in ('UIUX:11.9','UIUX:14.1','UIUX:14.2'):
        if rid not in rows: failures.append(f'missing successor acceptance row {rid}')
    lifecycle=json.loads((ROOT/'migration/acceptance/v1/validator_lifecycle.json').read_text())
    live={v['path'] for v in lifecycle['validators'] if str(v.get('status','')).startswith('live-')}
    if any('DebugWorkbench' in p for p in live): failures.append('Debug Workbench source validator remains live')
    if failures:
        for x in failures: print('Diagnostics successor acceptance failure: '+x,file=sys.stderr)
        return 1
    print('System Diagnostics successor acceptance PASS (AUD-FIX-13 evidence authority).')
    return 0
if __name__=='__main__': raise SystemExit(main())
