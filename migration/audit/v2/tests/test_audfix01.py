from __future__ import annotations
import json, sys, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'scripts'))
from audfix01_core import *
from validate_audfix01_contract import gate_d_phase_failures

class AudFix01(unittest.TestCase):
    def test_requirement_index_has_every_gap(self):
        r=load_json(ROOT/'migration/requirements/v2/requirements.json')
        gaps=[x for x in r['requirements'] if x['id'].startswith('GAP:')]
        self.assertEqual(33,len(gaps)); self.assertEqual(33,len({x['id'] for x in gaps}))
        self.assertTrue(all(x.get('owner') and x.get('closure_criteria') for x in gaps))
    def test_requirement_source_hashes_are_current(self):
        import hashlib
        r=load_json(ROOT/'migration/requirements/v2/requirements.json')
        source=ROOT/'migration/requirements/v2/source'
        for name,digest in r['source_sha256'].items():
            self.assertEqual(digest,hashlib.sha256((source/name).read_bytes()).hexdigest(),name)
    def test_source_guard_lifecycle_is_explicit(self):
        ledger=load_json(ROOT/'migration/ledger/source_token_contract_tests.json')
        self.assertEqual('dustui.source-contract-lifecycle.v2',ledger['schema'])
        for item in ledger['tests']:
            self.assertIn(item['status'],{'successor-aware-bridge','historical-tripwire'})
            self.assertIn(item['acceptance_authority'],{'invariant-only','temporary-migration-guard'})
            if item['status']=='successor-aware-bridge':
                body=read_text(ROOT,item['path'])
                self.assertIn('AUD-FIX successor-aware',body,item['path'])
    def test_current_truth_has_no_independent_gate_claims(self):
        self.assertEqual([],independent_gate_claims(ROOT))
        self.assertEqual([],predecessor_ui_authority_paths(ROOT))
    def test_gate_d_cannot_close_from_source_absence(self):
        reg={'source_snapshot_sha256':'x','nodes':[{'id':n,'status':'not-run','requires_artifact_binding':False} for n in REQUIRED_GATE_D_NODES]}
        with tempfile.TemporaryDirectory() as d:
            q=compute_qualification(Path(d),reg)
        self.assertFalse(q['gate_d']['qualified'])
    def test_gate_d_requires_bindings(self):
        nodes=[]
        for n in REQUIRED_GATE_D_NODES:
            nodes.append({'id':n,'status':'pass','requires_artifact_binding':n in {'runtime_apk_binding','devtool_artifact_identity'},'binding':{'source_tree_sha256':'tree'}})
        reg={'source_snapshot_sha256':'x','nodes':nodes}
        q=compute_qualification(ROOT,reg)
        self.assertFalse(q['gate_d']['qualified']); self.assertIn('runtime_apk_binding',q['gate_d']['invalid_binding_nodes'])
    def test_gate_d_phase_prequalification(self):
        state={
            'gate_d':{'qualified':False,'status':'open'},
            'ov14':{'status':'blocked-on-gate-d'},
            'gate_e':{'qualified':False,'status':'blocked-on-gate-d-and-ov14-retirement'},
        }
        self.assertEqual([],gate_d_phase_failures(state,ROOT))
        state['gate_d']['status']='qualified'
        self.assertIn('AUD001_GATE_D',{x['code'] for x in gate_d_phase_failures(state,ROOT)})

    def test_gate_d_phase_successor(self):
        ancestor='a'*64
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            q=root/'migration/qualification/v1'
            q.mkdir(parents=True)
            for name in ('gate_d_evidence.json','gate_d_bundle.json'):
                (q/name).write_text(json.dumps({'source_snapshot_sha256':ancestor}))
            state={
                'gate_d':{
                    'qualified':True,
                    'status':'qualified',
                    'passing_source_tree_sha256':ancestor,
                },
                'ov14':{'status':'physically-retired'},
                'gate_e':{'qualified':False,'status':'eligible-for-qualification'},
            }
            self.assertEqual([],gate_d_phase_failures(state,root))
            state['gate_e']={'qualified':True,'status':'qualified'}
            self.assertEqual([],gate_d_phase_failures(state,root))
            (q/'gate_d_bundle.json').write_text(json.dumps({'source_snapshot_sha256':'b'*64}))
            self.assertIn(
                'AUD001_GATE_D_BUNDLE_ANCESTOR',
                {x['code'] for x in gate_d_phase_failures(state,root)},
            )

    def test_negative_fixtures(self):
        for p in sorted((ROOT/'migration/fixtures/audfix01').glob('*.json')):
            o=load_json(p); self.assertEqual(sorted(o['expected_findings']),sorted(evaluate_fixture(o)),p.name)
    def test_ov14_is_not_gate_d_authority(self):
        o=load_json(ROOT/'migration/ledger/ov14_legacy_retirement_status.json')
        self.assertNotIn('gate_d_truth',o); self.assertNotIn('ov15_status',o)
        self.assertEqual('migration/qualification/v1/current_state.json',o['qualification_state_source'])
    def test_gate_d_workflow_precedes_retirement(self):
        s=read_text(ROOT,'.devtool.toml')
        m=__import__('re').search(r'(?m)^gate_d\s*=\s*\[(.*?)\]$',s)
        self.assertIsNotNone(m); wf=m.group(1)
        self.assertIn('job:qualification_state',wf); self.assertNotIn('job:retirement_gate',wf)

if __name__=='__main__': unittest.main()
