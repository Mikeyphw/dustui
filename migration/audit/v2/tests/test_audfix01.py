from __future__ import annotations
import json, sys, tempfile, tomllib, unittest
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
        self.assertEqual('dustui.validator-lifecycle.v1',ledger['schema'])
        self.assertEqual('acceptance-governance-only',ledger['authority'])
        for item in ledger['validators']:
            self.assertIn(item['status'],{'archived-historical','live-fixture-contract'})
            self.assertIn(item['acceptance_authority'],{'none','fixture-contract'})
            if item['status']=='archived-historical':
                self.assertEqual('none',item['acceptance_authority'])
                self.assertTrue(item.get('archive_path'),item['path'])
                self.assertTrue((ROOT/item['archive_path']).is_file(),item['path'])
                self.assertTrue(item.get('successor_evidence'),item['path'])
            else:
                self.assertTrue((ROOT/item['path']).is_file(),item['path'])
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
        cfg=tomllib.loads(read_text(ROOT,'.devtool.toml'))
        workflow=cfg['targets']['dustui_android']['workflows']['gate_d']
        refs={node.get('ref') if isinstance(node,dict) else node for node in workflow}
        self.assertIn('job:qualification_state',refs)
        self.assertNotIn('job:retirement_gate',refs)
        self.assertNotIn('job:audfix16_retirement_evidence',refs)

    def test_gate_d_successor_leaf_validators_use_canonical_phase_contract(self):
        for rel in (
            'scripts/validate_audfix03_contract.py',
            'scripts/validate_audfix04_contract.py',
            'scripts/validate_audfix05_contract.py',
        ):
            body=read_text(ROOT,rel)
            self.assertIn('from validate_audfix01_contract import gate_d_phase_failures',body,rel)
            self.assertIn('ROADMAP_GATE_D_SUCCESSOR_STATE',body,rel)
            self.assertNotIn('ROADMAP_GATE_D_MUST_STAY_OPEN',body,rel)
            self.assertNotIn("'ROADMAP_GATE_D_OPEN'",body,rel)

    def test_audfix04_retired_android_fixture_has_successor_evidence(self):
        rel='android/app/src/test/kotlin/com/mikeyphw/dustui/automation/AudFix04AutomationContractTest.kt'
        lifecycle=load_json(ROOT/'migration/acceptance/v1/validator_lifecycle.json')
        entry=next(item for item in lifecycle['validators'] if item.get('path')==rel)
        self.assertFalse((ROOT/rel).exists())
        self.assertEqual('archived-historical',entry['status'])
        self.assertEqual('AUD-FIX-16',entry['retirement_owner'])
        self.assertEqual('none',entry['acceptance_authority'])
        self.assertTrue((ROOT/entry['archive_path']).is_file())
        self.assertTrue({'workflow:aud_fix_11','gate:durable_operation_terminal_truth'}.issubset(set(entry['successor_evidence'])))

    def test_post_gate_e_temporary_resume_authority_is_retired(self):
        cfg=tomllib.loads(read_text(ROOT,'.devtool.toml'))
        workflows=cfg['targets']['dustui_android']['workflows']
        wrapper=cfg.get('wrapper',{}).get('commands',{})
        self.assertNotIn('gate_e_fast',workflows)
        self.assertNotIn('aud-fix-17-fast',wrapper)
        contract=read_text(ROOT,'scripts/validate_audfix17_contract.py')
        self.assertIn('"gate_e_fast" not in workflows',contract)
        self.assertIn('"aud-fix-17-fast" not in wrapper',contract)

    def test_live_operator_docs_are_post_gate_e(self):
        rels=(
            'README.md','RELEASE_CHECKLIST.md','VALIDATION.md','KNOWN_ISSUES.md',
            'android/VALIDATION_REMEDIATION.md','migration/qualification/v1/README.md',
        )
        bodies={rel:read_text(ROOT,rel) for rel in rels}
        joined='\n'.join(bodies.values())
        for stale in (
            'Gate E remains pending',
            'Gate E is the next qualification boundary',
            'Gate D remains open',
            'blocks `scripts/final-rc-gate.sh`',
            '`release-gate.sh` runs the same audit',
        ):
            self.assertNotIn(stale,joined,stale)
        self.assertIn('Gate E is qualified',bodies['README.md'])
        self.assertIn('Gate E is qualified',bodies['migration/qualification/v1/README.md'])


if __name__=='__main__': unittest.main()
