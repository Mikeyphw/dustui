#!/usr/bin/env python3
"""AUD-FIX successor-aware. AUD-FIX-13 successor UI acceptance alias for the historical portrait validator."""
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
    for rid in ('UIUX:6.1','UIUX:7.1','UIUX:7.2','UIUX:7.3','UIUX:14.2'):
        if rid not in rows: failures.append(f'missing successor UI acceptance row {rid}')
    if len(matrix.get('user_tasks',[])) != 14: failures.append('complete UX task matrix is missing')
    if len(matrix.get('visual_product_rules',[])) != 10: failures.append('visual/product acceptance rules are incomplete')
    if failures:
        for x in failures: print('Semantic/adaptive UI acceptance failure: '+x,file=sys.stderr)
        return 1
    print('Semantic/adaptive UI successor acceptance PASS (AUD-FIX-13 evidence authority).')
    return 0
if __name__=='__main__': raise SystemExit(main())
