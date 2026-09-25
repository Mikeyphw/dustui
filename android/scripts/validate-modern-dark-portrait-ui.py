#!/usr/bin/env python3
"""AUD-FIX successor-aware UI acceptance bridge.

The historical portrait contract is validated only while the predecessor shell
is still production-reachable. Once the semantic shell replaces it, this
validator switches to invariant checks and never requires retired symbols.
"""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def read(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''

def main()->int:
    fail=[]
    design=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt')
    app=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt')
    routes=read('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/shell/DustShellRouteContract.kt')
    predecessor='enum class DustDestination' in design
    if predecessor:
        for rel,token in [
            ('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/Theme.kt','object DustModernColors'),
            ('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/ModernDustComponents.kt','fun DustHeroCard'),
            ('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt','Whole-scan search'),
            ('android/app/src/main/kotlin/com/mikeyphw/dustui/ui/CleanupRecommendationsScreen.kt','Cleanup cockpit'),
        ]:
            if token not in read(rel): fail.append(f'predecessor invariant missing {token!r} in {rel}')
    else:
        for token in ['Overview','Files','Clean','Apps','Activity','System']:
            if token not in routes: fail.append(f'successor semantic route missing: {token}')
        if 'DustSemanticNavigation.areas' not in app: fail.append('successor shell does not render stable semantic area list')
    try:
        idx=json.loads(read('migration/requirements/v2/requirements.json'))
        ids={r.get('id') for r in idx.get('requirements',[])}
        if not {'UIUX:6.1','UIUX:7.1','UIUX:7.2','UIUX:14.2'} <= ids: fail.append('authoritative UI/UX requirement index incomplete')
    except Exception as exc: fail.append(f'requirement index invalid: {exc}')
    if fail:
        print('Successor-aware UI acceptance audit failed:',file=sys.stderr)
        for x in fail: print('- '+x,file=sys.stderr)
        return 1
    print(f"Successor-aware UI acceptance audit passed ({'predecessor-bridge' if predecessor else 'semantic-shell'} mode).")
    return 0
if __name__=='__main__': raise SystemExit(main())
