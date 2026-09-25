#!/usr/bin/env python3
from __future__ import annotations

import argparse, json, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from audfix01_core import (  # noqa:E402
    REQUIRED_GATE_D_NODES, independent_gate_claims, load_json,
    predecessor_ui_authority_paths, repository_observations,
)


def finding(fid: str, detail: str, family: str, blocking_fix01: bool=False):
    return {'id': fid, 'family': family, 'detail': detail, 'blocks_aud_fix_01': blocking_fix01}


def route_names() -> set[str]:
    text = ''
    for rel in [
        'android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustSemanticNavigation.kt',
        'android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustDestination.kt',
        'android/app/src/main/kotlin/com/mikeyphw/dustui/ui/DustShellNavigation.kt',
    ]:
        p=ROOT/rel
        if p.is_file(): text += '\n'+p.read_text(errors='replace')
    return set(re.findall(r'\b(Overview|Files|Clean|Apps|Activity|System|Diagnostics|Certification|DeletionReports|Settings|Scan|Search|Duplicates|Residue)\b', text))


def production_uses(symbol: str) -> int:
    base=ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui'
    count=0
    if base.exists():
        for p in base.rglob('*.kt'):
            s=p.read_text(errors='replace')
            if symbol in s and 'Contract' not in p.name:
                count += s.count(symbol)
    return count


def evaluate() -> list[dict]:
    out=[]
    state_path=ROOT/'migration/qualification/v1/current_state.json'
    evidence_path=ROOT/'migration/qualification/v1/gate_d_evidence.json'
    req_path=ROOT/'migration/requirements/v2/requirements.json'
    for p in (state_path,evidence_path,req_path):
        if not p.is_file(): out.append(finding('AF01-CANONICAL-MISSING',str(p.relative_to(ROOT)),'AUD-001',True))
    if not (state_path.is_file() and evidence_path.is_file()): return out
    state=load_json(state_path); evidence=load_json(evidence_path)
    claims=independent_gate_claims(ROOT)
    if claims: out.append(finding('AF01-INDEPENDENT-GATE-CLAIMS',', '.join(claims),'AUD-001',True))
    stale=predecessor_ui_authority_paths(ROOT)
    if stale: out.append(finding('AF01-STALE-UI-AUTHORITY',', '.join(stale),'UIX-009',True))
    nodes={x.get('id'):x for x in evidence.get('nodes',[]) if isinstance(x,dict)}
    missing=[x for x in REQUIRED_GATE_D_NODES if x not in nodes]
    if missing: out.append(finding('AF01-MISSING-EVIDENCE-TOPOLOGY',', '.join(missing),'AUD-021',True))
    if state.get('gate_d',{}).get('qualified') and (missing or state['gate_d'].get('nonpassing_nodes')):
        out.append(finding('AF01-FALSE-GATE-D-CLOSURE','qualified despite incomplete evidence','AUD-001',True))
    if state.get('gate_d',{}).get('status') != 'open':
        out.append(finding('AF01-GATE-D-NOT-OPEN',str(state.get('gate_d')),'AUD-001',True))

    obs=repository_observations(ROOT)
    if obs['generic_tooling_duplication_paths']:
        out.append(finding('LATER-RAW-TOOLING',', '.join(obs['generic_tooling_duplication_paths']),'AUD-015'))
    if obs['legacy_navigation_present']:
        out.append(finding('LATER-LEGACY-NAV','legacy navigation remains reachable/source-present','AUD-005'))
    if obs['legacy_python_textual_present']:
        out.append(finding('LATER-PYTHON-TEXTUAL','legacy Python/Textual remains','AUD-003'))
    if obs['room_artifacts_present'] or obs['ndjson_lifecycle_present']:
        out.append(finding('LATER-ROOM-NDJSON','legacy Room/NDJSON remains','AUD-004'))
    names=route_names()
    required_top={'Overview','Files','Clean','Apps','Activity','System'}
    if not required_top.issubset(names):
        out.append(finding('LATER-ROUTE-TREE',f'missing top-level routes {sorted(required_top-names)}','UIX-001'))
    if production_uses('DustContentState') == 0:
        out.append(finding('LATER-SHARED-STATE-UNUSED','DustContentState has no production consumer','UIX-003'))
    if production_uses('DustActionGate') == 0:
        out.append(finding('LATER-ACTION-GATE-UNUSED','DustActionGate has no production consumer','UIX-004'))
    hist=['historical_gate_a_exo','historical_gate_b_exo_arm64','historical_gate_c_exo']
    if any(nodes.get(x,{}).get('status')!='pass' for x in hist):
        out.append(finding('LATER-HISTORICAL-EXO','Gate A/B/C EXO evidence not qualified','AUD-020'))
    return out


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--scope',default='all',choices=['all','AUD-FIX-01']); ap.add_argument('--json',action='store_true')
    args=ap.parse_args(); findings=evaluate()
    blocking=[x for x in findings if x['blocks_aud_fix_01']]
    payload={'schema':'dustui.full-roadmap-conformance.v2','scope':args.scope,'aud_fix_01_blockers':blocking,'findings':findings}
    if args.json: print(json.dumps(payload,indent=2))
    else:
        print(f"FULL ROADMAP CONFORMANCE [{args.scope}]")
        for x in findings: print(f"- {x['id']} [{x['family']}]: {x['detail']}")
        print(f"AUD-FIX-01 blockers: {len(blocking)}")
    return 1 if (blocking if args.scope=='AUD-FIX-01' else findings) else 0
if __name__=='__main__': raise SystemExit(main())
