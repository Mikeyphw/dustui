#!/usr/bin/env python3
from __future__ import annotations

import argparse, hashlib, json, os, shutil, subprocess, sys, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/evidence/gate-d'

def sha(p:Path)->str:
    h=hashlib.sha256();
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def run(*a:str)->subprocess.CompletedProcess[str]:
    return subprocess.run(a,cwd=ROOT,text=True,capture_output=True)

def write(name:str,obj:dict)->int:
    OUT.mkdir(parents=True,exist_ok=True); p=OUT/f'{name}.json'; p.write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n'); print(json.dumps(obj,indent=2,sort_keys=True)); return 0 if obj.get('status')=='pass' else 1

def rooted()->int:
    adb=shutil.which('adb')
    obj={'schema':'dustui.gate-d.rooted-arm64-runtime.v1','status':'fail'}
    if not adb: obj['reason']='adb not found'; return write('rooted-arm64-runtime',obj)
    cp=run(adb,'devices'); devices=[ln.split()[0] for ln in cp.stdout.splitlines()[1:] if ln.strip().endswith('\tdevice')]
    if len(devices)!=1: obj['reason']=f'exactly one adb device required, found {len(devices)}'; obj['devices']=devices; return write('rooted-arm64-runtime',obj)
    serial=devices[0]
    abi=run(adb,'-s',serial,'shell','getprop','ro.product.cpu.abi').stdout.strip()
    roots=[]
    for argv in ([adb,'-s',serial,'shell','su','-c','id'],[adb,'-s',serial,'shell','su','0','id']):
        c=subprocess.run(argv,text=True,capture_output=True); roots.append({'argv':argv[-3:],'rc':c.returncode,'stdout':c.stdout.strip(),'stderr':c.stderr.strip()})
        if c.returncode==0 and 'uid=0' in c.stdout:
            obj.update(status='pass',device_serial=serial,device_arch=abi,root_probe=roots[-1]); break
    else: obj.update(reason='root shell unavailable',device_serial=serial,device_arch=abi,root_probes=roots)
    if 'arm64' not in abi: obj['status']='fail'; obj['reason']=f'ARM64 device required, got {abi}'
    return write('rooted-arm64-runtime',obj)

def runtime_binding()->int:
    generated=ROOT/'android/app/build/generated/goRuntime/jniLibs/arm64-v8a/libdustui_go.so'
    apks=sorted((ROOT/'android/app/build/outputs/apk').glob('**/*.apk'))
    obj={'schema':'dustui.gate-d.runtime-apk-binding.v1','status':'fail','generated_runtime':str(generated.relative_to(ROOT))}
    if not generated.is_file(): obj['reason']='generated Go runtime missing'; return write('runtime-apk-binding',obj)
    gsha=sha(generated); matches=[]
    for apk in apks:
        try:
            with zipfile.ZipFile(apk) as z:
                name='lib/arm64-v8a/libdustui_go.so'
                if name not in z.namelist(): continue
                data=z.read(name); ah=hashlib.sha256(data).hexdigest()
                matches.append({'apk':str(apk.relative_to(ROOT)),'apk_sha256':sha(apk),'runtime_sha256':ah,'matches_generated':ah==gsha})
        except zipfile.BadZipFile: pass
    if matches and all(m['matches_generated'] for m in matches): obj.update(status='pass',generated_runtime_sha256=gsha,apks=matches)
    else: obj.update(reason='no packaged ARM64 runtime exactly matches generated runtime',generated_runtime_sha256=gsha,apks=matches)
    return write('runtime-apk-binding',obj)

def artifact_identity()->int:
    tree=run('git','rev-parse','HEAD^{tree}'); ver=run('devtool','--version') if shutil.which('devtool') else None
    obj={'schema':'dustui.gate-d.devtool-artifact-identity.v1','status':'pass' if tree.returncode==0 and ver and ver.returncode==0 else 'fail','source_tree_sha256':tree.stdout.strip() if tree.returncode==0 else None,'devtool_version':(ver.stdout or ver.stderr).strip() if ver else None,'devtool_run_id':os.environ.get('DEVTOOL_RUN_ID') or os.environ.get('DEVTOOL_RUN_UUID'),'note':'Canonical EXO run/log/artifact hashes are bound by Gate-D registry finalization; this leaf proves source-tree and installed Devtool identity.'}
    if obj['status']!='pass': obj['reason']='git tree or devtool version unavailable'
    return write('devtool-artifact-identity',obj)

def reachability()->int:
    checks=[['python3','scripts/validate_audfix07_contract.py','--check','host-reachability'],['python3','scripts/validate_audfix07_contract.py','--check','product-identity'],['python3','scripts/validate_audfix08_contract.py'],['python3','scripts/validate_audfix09_contract.py']]
    results=[]; ok=True
    for cmd in checks:
        c=run(*cmd); results.append({'command':cmd,'rc':c.returncode,'stdout_tail':c.stdout[-2000:],'stderr_tail':c.stderr[-1000:]}); ok &= c.returncode==0
    obj={'schema':'dustui.gate-d.production-reachability.v1','status':'pass' if ok else 'fail','checks':results,'physical_retirement_deferred_to':'AUD-FIX-16'}
    if not ok: obj['reason']='one or more successor reachability contracts failed'
    return write('production-reachability',obj)

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('section',choices=['rooted-runtime','runtime-apk-binding','devtool-artifact-identity','production-reachability']); a=ap.parse_args()
    return {'rooted-runtime':rooted,'runtime-apk-binding':runtime_binding,'devtool-artifact-identity':artifact_identity,'production-reachability':reachability}[a.section]()
if __name__=='__main__': raise SystemExit(main())
