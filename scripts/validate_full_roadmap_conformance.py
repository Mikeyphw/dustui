#!/usr/bin/env python3
from __future__ import annotations

import argparse, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from audfix01_core import (  # noqa:E402
    REQUIRED_GATE_D_NODES, HISTORICAL_GATE_D_NODES, independent_gate_claims, load_json,
    predecessor_ui_authority_paths, repository_observations,
)
from audfix13_acceptance import lifecycle_failures, ui_matrix_failures, metadata_failures, docs_failures, evaluate_counterexample  # noqa:E402


def finding(fid: str, detail: str, family: str, blocking_fix01: bool=False, blocking_fix13: bool=False, blocking_fix16: bool=False):
    return {'id': fid, 'family': family, 'detail': detail, 'blocks_aud_fix_01': blocking_fix01, 'blocks_aud_fix_13': blocking_fix13, 'blocks_aud_fix_16': blocking_fix16}


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
    if state.get('gate_d',{}).get('status') not in {'open','qualified'}:
        out.append(finding('AF01-GATE-D-STATE-INVALID',str(state.get('gate_d')),'AUD-001',True))

    obs=repository_observations(ROOT)
    if obs['generic_tooling_duplication_paths']:
        out.append(finding('LATER-RAW-TOOLING',', '.join(obs['generic_tooling_duplication_paths']),'AUD-015',blocking_fix16=True))
    if obs['legacy_navigation_present']:
        out.append(finding('LATER-LEGACY-NAV','legacy navigation remains reachable/source-present','AUD-005'))
    if obs['legacy_python_textual_present']:
        out.append(finding('LATER-PYTHON-TEXTUAL','legacy Python/Textual remains','AUD-003',blocking_fix16=True))
    if obs['room_artifacts_present'] or obs['ndjson_lifecycle_present']:
        out.append(finding('LATER-ROOM-NDJSON','legacy Room/NDJSON remains','AUD-014',blocking_fix16=True))
    if obs.get('python_textual_oracle_present'):
        out.append(finding('AF16-LIVE-PARITY-ORACLE','Python/Textual parity oracle remains in the live migration surface','AUD-003',blocking_fix16=True))
    if obs.get('predecessor_source_token_paths'):
        out.append(finding('AF16-PREDECESSOR-SOURCE-GUARDS',', '.join(obs['predecessor_source_token_paths']),'AUD-016',blocking_fix16=True))
    if state.get('gate_d',{}).get('qualified'):
        if state.get('ov14',{}).get('status') != 'physically-retired':
            out.append(finding('AF16-OV14-NOT-RETIRED',str(state.get('ov14')),'AUD-014',blocking_fix16=True))
        if state.get('gate_e',{}).get('status') != 'eligible-for-qualification':
            out.append(finding('AF16-GATE-E-NOT-ELIGIBLE',str(state.get('gate_e')),'AUD-021',blocking_fix16=True))
        retirement=ROOT/'migration/qualification/v1/ov14_retirement.json'
        if not retirement.is_file():
            out.append(finding('AF16-RETIREMENT-EVIDENCE-MISSING','ov14_retirement.json missing','AUD-021',blocking_fix16=True))
    names=route_names()
    required_top={'Overview','Files','Clean','Apps','Activity','System'}
    if not required_top.issubset(names):
        out.append(finding('LATER-ROUTE-TREE',f'missing top-level routes {sorted(required_top-names)}','UIX-001'))
    if production_uses('DustContentState') == 0:
        out.append(finding('LATER-SHARED-STATE-UNUSED','DustContentState has no production consumer','UIX-003'))
    if production_uses('DustActionGate') == 0:
        out.append(finding('LATER-ACTION-GATE-UNUSED','DustActionGate has no production consumer','UIX-004'))
    hist=list(HISTORICAL_GATE_D_NODES)
    if any(nodes.get(x,{}).get('status')!='pass' for x in hist):
        out.append(finding('AF14-HISTORICAL-EXO','Gate A/B/C exact-tree EXO evidence unresolved','AUD-019'))
    topo_path=ROOT/'migration/qualification/v1/gate_d_topology.json'
    hist_path=ROOT/'migration/qualification/v1/historical_gate_evidence.json'
    if not topo_path.is_file():
        out.append(finding('AF14-GATE-D-TOPOLOGY','gate_d_topology.json missing','AUD-018'))
    else:
        topo=load_json(topo_path)
        topo_ids={x.get('id') for x in topo.get('nodes',[]) if isinstance(x,dict)}
        req=set(REQUIRED_GATE_D_NODES)
        if topo_ids != req or set(topo.get('required_nodes',[])) != req:
            out.append(finding('AF14-GATE-D-TOPOLOGY',f'topology mismatch missing={sorted(req-topo_ids)} extra={sorted(topo_ids-req)}','AUD-018'))
        for node in topo.get('nodes',[]):
            nid=node.get('id'); histnode=nid in HISTORICAL_GATE_D_NODES
            if bool(node.get('historical')) != histnode:
                out.append(finding('AF14-GATE-D-TOPOLOGY',f'{nid}: historical classification mismatch','AUD-018'))
            if histnode and node.get('same_gate_d_tree'):
                out.append(finding('AF14-HISTORICAL-BINDING',f'{nid}: historical node incorrectly bound to Gate-D tree','AUD-019'))
            if not histnode and not node.get('same_gate_d_tree'):
                out.append(finding('AF14-TREE-BINDING',f'{nid}: current node lacks same-tree binding','AUD-018'))
    if not hist_path.is_file():
        out.append(finding('AF14-HISTORICAL-INVENTORY','historical_gate_evidence.json missing','AUD-019'))
    for detail in lifecycle_failures(ROOT):
        out.append(finding('AF13-VALIDATOR-LIFECYCLE',detail,'AUD-016',blocking_fix13=True))
    for detail in ui_matrix_failures(ROOT):
        out.append(finding('AF13-UI-ACCEPTANCE',detail,'UIX-009',blocking_fix13=True))
    for detail in metadata_failures(ROOT):
        out.append(finding('AF13-METADATA',detail,'AUD-017',blocking_fix13=True))
    for detail in docs_failures(ROOT):
        out.append(finding('AF13-DOC-AUTHORITY',detail,'AUD-023',blocking_fix13=True))
    fixture_path=ROOT/'migration/acceptance/v1/fixtures/counterexamples.json'
    if not fixture_path.is_file():
        out.append(finding('AF13-COUNTEREXAMPLES','counterexample fixture missing','AUD-021',blocking_fix13=True))
    else:
        fixture=load_json(fixture_path)
        for case in fixture.get('cases',[]):
            detected=set(evaluate_counterexample(case)); expected=set(case.get('expected_failures',[]))
            if detected != expected:
                out.append(finding('AF13-COUNTEREXAMPLES',f"{case.get('id')}: detected={sorted(detected)} expected={sorted(expected)}",'AUD-021',blocking_fix13=True))
    return out


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--scope',default='all',choices=['all','AUD-FIX-01','AUD-FIX-13','AUD-FIX-14','AUD-FIX-16','GATE-D']); ap.add_argument('--json',action='store_true')
    args=ap.parse_args(); findings=evaluate()
    blocking=[x for x in findings if x['blocks_aud_fix_01']]
    blocking13=[x for x in findings if x.get('blocks_aud_fix_13')]
    topology_families={'AUD-018','AUD-021','AUD-023'}
    blocking14=[x for x in findings if x['family'] in topology_families]
    blocking16=[x for x in findings if x.get('blocks_aud_fix_16')]
    gate_d_allowed_open={'AUD-014','AUD-015'}
    gate_d_blockers=[x for x in findings if x['family'] not in gate_d_allowed_open]
    payload={'schema':'dustui.full-roadmap-conformance.v4','scope':args.scope,'aud_fix_01_blockers':blocking,'aud_fix_13_blockers':blocking13,'aud_fix_14_topology_blockers':blocking14,'aud_fix_16_blockers':blocking16,'gate_d_blockers':gate_d_blockers,'findings':findings}
    if args.json: print(json.dumps(payload,indent=2))
    else:
        print(f"FULL ROADMAP CONFORMANCE [{args.scope}]")
        for x in findings: print(f"- {x['id']} [{x['family']}]: {x['detail']}")
        print(f"AUD-FIX-01 blockers: {len(blocking)}")
        print(f"AUD-FIX-13 blockers: {len(blocking13)}")
        print(f"AUD-FIX-14 topology blockers: {len(blocking14)}")
        print(f"AUD-FIX-16 blockers: {len(blocking16)}")
        print(f"Gate-D blockers: {len(gate_d_blockers)}")
    if args.scope=='AUD-FIX-01': selected=blocking
    elif args.scope=='AUD-FIX-13': selected=blocking13
    elif args.scope=='AUD-FIX-14': selected=blocking14
    elif args.scope=='AUD-FIX-16': selected=blocking16
    elif args.scope=='GATE-D': selected=gate_d_blockers
    else: selected=findings
    return 1 if selected else 0
if __name__=='__main__': raise SystemExit(main())
