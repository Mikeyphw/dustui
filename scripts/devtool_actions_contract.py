#!/usr/bin/env python3
"""Read-only source and hosted workflow interface check (not a release/device gate)."""
import json, pathlib, sys, tomllib
r=pathlib.Path(__file__).resolve().parents[1]
c=tomllib.loads((r/'.devtool.toml').read_text())
assert c.get('wrapper',{}).get('schema')==2, 'wrapper schema 2 required'
assert 'ci-preflight' in c['wrapper']['commands']
for name, cfg in c['wrapper']['commands'].items():
    if name.startswith('ci-'):
        target=cfg['target']; wf=cfg['workflow']
        assert wf in c['targets'][target].get('workflows',{}),(name,target,wf)
        steps=c['targets'][target]['workflows'][wf]
        assert steps, name
        if isinstance(steps[0],dict):
            ids=set()
            for step in steps:
                assert step['id'] not in ids,(name,step['id'])
                assert set(step['depends_on'])<=ids, (name,step)
                ids.add(step['id'])
        elif not isinstance(steps[0],str): raise AssertionError((name,'unsupported steps'))
files=sorted((r/'.github/workflows').glob('*.yml'))
assert files, 'no workflows'
assert any('workflow_dispatch:' in f.read_text() for f in files), 'Devtool needs at least one manually dispatchable workflow'
for f in files:
    if f.name.startswith('devtool-'):
        data=f.read_text()
        assert 'actions/checkout@' in data, f
        assert 'workflow_dispatch:' in data, f
print(json.dumps({'status':'PASS','workspace':r.name,'target_count':len(c['targets']), 'actions_workflows': [f.name for f in files]},sort_keys=True))
