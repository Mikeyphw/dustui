#!/usr/bin/env python3
"""AUD-FIX successor-aware diagnostics acceptance bridge."""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(sys.argv[1]) if len(sys.argv)>1 else Path.cwd()

def read(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''

def main()->int:
    fail=[]
    design=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt')
    app=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt')
    predecessor=('DustDestination.DEBUG' in design or 'DebugWorkbenchScreen(' in app)
    if predecessor:
        # Preserve only safety/diagnostic invariants; do not freeze exact workbench symbols.
        bundle=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt')
        events=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugEvents.kt')
        for token in ['redactDebugValue','noFileContents']:
            if token not in events+bundle: fail.append(f'predecessor diagnostics safety invariant missing: {token}')
        for forbidden in ['FileInputStream(','readBytes()']:
            if forbidden in bundle: fail.append(f'unsafe debug export token remains: {forbidden}')
    else:
        routes=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustShellRouteContract.kt')
        runtime=read('go/cmd/dustui-go/runtime_domain.go')
        if 'SystemDiagnostics' not in routes: fail.append('successor System diagnostics nested route missing')
        if not any(x in runtime for x in ['domain.diagnostics.health','domain.diagnostics.list']): fail.append('successor diagnostics Go authority not exposed')
    try:
        idx=json.loads(read('migration/requirements/v2/requirements.json'))
        ids={r.get('id') for r in idx.get('requirements',[])}
        if not {'UIUX:11.9','GAP:UIX-009'} <= ids: fail.append('diagnostics/UI authority requirements missing from index')
    except Exception as exc: fail.append(f'requirement index invalid: {exc}')
    if fail:
        print('Successor-aware diagnostics acceptance audit failed:',file=sys.stderr)
        for x in fail: print('- '+x,file=sys.stderr)
        return 1
    print(f"Successor-aware diagnostics acceptance audit passed ({'predecessor-bridge' if predecessor else 'system-diagnostics'} mode).")
    return 0
if __name__=='__main__': raise SystemExit(main())
