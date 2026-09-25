#!/usr/bin/env python3
"""AUD-FIX-01 contract + bounded audit loop.

This is intentionally a no-full-validation overlay validator. It verifies canonical
truth machinery, successor-aware guards, negative fixtures, and agreement of every
live migration validator that is in AUD-FIX-01 scope. Later roadmap gaps are
reported but do not become false AUD-FIX-01 failures.
"""
from __future__ import annotations
import argparse, hashlib, json, os, subprocess, sys, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

COMMANDS=[
 ['python3','scripts/refresh_qualification_state.py','--check'],
 ['python3','scripts/refresh_ov14_retirement_observation.py','--check'],
 ['python3','-m','unittest','migration.audit.v2.tests.test_audfix01','-v'],
 ['python3','scripts/validate_rem02_reconciliation_hotfix.py'],
 ['python3','scripts/validate_rem03_android_domain_cutover.py'],
 ['python3','scripts/validate_rem04_privileged_cleanup_cutover.py'],
 ['python3','scripts/validate_rem05_legacy_surface_retirement.py'],
 ['python3','scripts/validate_ov13_contract.py'],
 ['python3','scripts/validate_ov14_contract.py'],
 ['python3','scripts/validate_ov13_devtool_wrapper_exo_jobs.py'],
 ['python3','android/scripts/validate-modern-dark-portrait-ui.py'],
 ['python3','android/scripts/validate-debug-workbench-contract.py'],
 ['python3','scripts/validate_full_roadmap_conformance.py','--scope','AUD-FIX-01','--json'],
]

def run(cmd):
 print('  -> '+ ' '.join(cmd), file=sys.stderr, flush=True)
 try:
  p=subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
  return {'command':' '.join(cmd),'returncode':p.returncode,'output':p.stdout.strip()}
 except subprocess.TimeoutExpired as exc:
  output=(exc.stdout or '') if isinstance(exc.stdout,str) else (exc.stdout or b'').decode(errors='replace')
  return {'command':' '.join(cmd),'returncode':124,'output':('TIMEOUT after 15s\n'+output).strip()}

def sha(path):
 h=hashlib.sha256(); h.update(path.read_bytes()); return h.hexdigest()

def one_pass(index:int):
 results=[run(c) for c in COMMANDS]
 conformance=json.loads(results[-1]['output']) if results[-1]['returncode']==0 else {}
 later=sorted({x['family'] for x in conformance.get('findings',[]) if not x.get('blocks_aud_fix_01')})
 blocker_ids=sorted(x['id'] for x in conformance.get('aud_fix_01_blockers',[]))
 failures=[r for r in results if r['returncode']!=0]
 return {'pass':index,'results':results,'failures':[{'command':r['command'],'output':r['output']} for r in failures], 'aud_fix_01_blockers':blocker_ids,'later_gap_families_observed':later}

def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--audit-loop',action='store_true'); ap.add_argument('--write-evidence',action='store_true'); args=ap.parse_args()
 max_passes=3 if args.audit_loop else 1; passes=[]; previous=None; converged=False
 for i in range(1,max_passes+1):
  p=one_pass(i); passes.append(p)
  print(f'AUD-FIX-01 audit pass {i}: failures={len(p["failures"])} blockers={len(p["aud_fix_01_blockers"])} later={len(p["later_gap_families_observed"])}', file=sys.stderr, flush=True)
  sig=(tuple(p['aud_fix_01_blockers']),tuple(p['later_gap_families_observed']),tuple(x['command'] for x in p['failures']))
  if not p['failures'] and not p['aud_fix_01_blockers'] and previous==sig:
   converged=True; break
  previous=sig
 if not args.audit_loop: converged=not passes[-1]['failures'] and not passes[-1]['aud_fix_01_blockers']
 state=json.loads((ROOT/'migration/qualification/v1/current_state.json').read_text())
 evidence={
  'schema':'dustui.aud-fix-01-audit-loop.v1','overlay':'AUD-FIX-01','source_snapshot_sha256':state.get('source_snapshot_sha256'),
  'passes':passes,'converged':converged,'convergence_rule':'two consecutive complete passes with zero AUD-FIX-01 blockers/failures and identical later-gap family set',
  'gate_d_status':state.get('gate_d',{}).get('status'),'full_devtool_validation_run':False,
  'dynamic_devtool_note':'Dynamic wrapper check runs when devtool is installed; absence is explicitly deferred in this non-gate overlay.',
 }
 if args.write_evidence:
  out=ROOT/'migration/audit/v2/aud_fix_01_audit_loop.json'; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(evidence,indent=2,sort_keys=True)+'\n')
  status={'schema':'dustui.aud-fix-status.v1','overlay':'AUD-FIX-01','status':'complete' if converged else 'blocked','gate_d':'open','qualification_state_source':'migration/qualification/v1/current_state.json','audit_evidence':str(out.relative_to(ROOT)),'passes':len(passes),'aud_fix_01_blockers':passes[-1]['aud_fix_01_blockers'],'later_gap_families_observed':passes[-1]['later_gap_families_observed']}
  (ROOT/'migration/ledger/aud_fix_01_status.json').write_text(json.dumps(status,indent=2,sort_keys=True)+'\n')
 print(json.dumps({'converged':converged,'passes':len(passes),'aud_fix_01_blockers':passes[-1]['aud_fix_01_blockers'],'failures':[x['command'] for x in passes[-1]['failures']],'later_gap_families_observed':passes[-1]['later_gap_families_observed']},indent=2))
 return 0 if converged else 1
if __name__=='__main__': raise SystemExit(main())
