#!/usr/bin/env python3
from pathlib import Path
import json,sys
R=Path(__file__).resolve().parents[1]; fail=[]
def req(c,m):
    if not c: fail.append(m)
toml=(R/'.devtool.toml').read_text()
app='\n'.join(p.read_text(errors='replace') for p in (R/'android/app/src/main/kotlin').rglob('*.kt'))
status=json.loads((R/'migration/ledger/ov14_legacy_retirement_status.json').read_text())
req('[targets.dust_tui_legacy]' not in toml,'legacy Python Devtool target remains')
req('DustNavigationModel' not in app,'legacy DustNavigationModel remains in production')
req('DustShellNavigation' in app,'current adaptive shell navigation missing')
for rel in ['android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleasePackaging.kt','android/app/src/main/kotlin/com/mikeyphw/dustui/data/ReleaseReadiness.kt']:
    req(not (R/rel).exists(),f'app-local release truth remains: {rel}')
req(status.get('blockers')==[],'retirement ledger must have zero blockers')
req(status.get('status')=='closed' and status.get('gate_d_truth')=='closed','Gate D must be closed')
req(status.get('ov15_status')=='ready_for_gate_e','OV15 must be ready for Gate E')
if fail:
 print('REM-05 legacy surface retirement FAIL',file=sys.stderr); [print(' - '+x,file=sys.stderr) for x in fail]; raise SystemExit(1)
print('REM-05 legacy surface/release-truth retirement PASS (Gate D closed; OV15 Gate E ready)')
