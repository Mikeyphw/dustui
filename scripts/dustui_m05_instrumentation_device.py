#!/usr/bin/env python3
"""Read-only plan or explicitly approved ADB release instrumentation, no root or cleanup.

Usage: python3 scripts/dustui_m05_instrumentation_device.py plan --artifact-dir DIR
       python3 scripts/dustui_m05_instrumentation_device.py run --artifact-dir DIR --serial DEVICE --allow-app-replace --allow-test-install
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

EXPECTED_CERT = '5601d49884487a5d8f378b7665e37fff8b6808e7a1a038a465de1d07c9a5ef8b'
RELEASE_PKG = 'com.mikeyphw.dustui'
TEST_PKG = RELEASE_PKG + '.test'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'
ALLOWLIST = [
    'com.mikeyphw.dustui.go.M05ReleaseReadOnlyInstrumentedTest',
    'com.mikeyphw.dustui.go.GoChildLaunchIsolationInstrumentedTest',
    'com.mikeyphw.dustui.ui.AdaptiveRouteContinuityInstrumentedTest',
]

class DeviceError(ValueError):
    pass

def call(cmd: list[str], timeout: int = 90, allow_failure: bool = False):
    try:
        p = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise DeviceError('device tool missing/timed out: ' + str(exc)[:180]) from exc
    if p.returncode != 0 and not allow_failure:
        raise DeviceError('command failed (%s): %s\n%s' % (p.returncode, ' '.join(cmd[:4]), p.stderr[-400:]))
    return p

def find_one(folder: Path, name: str) -> Path:
    matches = sorted(x for x in folder.rglob(name) if x.is_file() and not x.is_symlink())
    if len(matches) != 1:
        raise DeviceError('required unique artifact missing/ambiguous: '+name)
    return matches[0]

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def sdktool(name: str):
    # Termux's bundled aapt2/apksigner are acceptable; SDK build tools also work.
    path = shutil.which(name)
    if not path:
        raise DeviceError(name + ' not found; install Termux aapt2/apksigner')
    return path

def signer(apk: Path) -> str:
    out = call([sdktool('apksigner'),'verify','--verbose','--print-certs',str(apk)]).stdout
    if not re.search(r'(?mi)^Verified using v2 scheme[^\r\n]*:\s*true\s*$',out):
        raise DeviceError('cryptographic APK v2 verification failed')
    found = re.findall(r'(?mi)^(?:Signer #[0-9]+|V[234](?:\.[0-9]+)? Signer):? certificate SHA-256 digest:[ \t]*([0-9a-f]{64})[ \t]*$',out)
    if not found or set(x.lower() for x in found) != {EXPECTED_CERT}:
        raise DeviceError('APK signer differs from expected existing permanent certificate')
    return EXPECTED_CERT

def package_info(apk: Path):
    out=call([sdktool('aapt2'),'dump','badging',str(apk)]).stdout
    p=re.search(r"(?m)^package: name='([^']+)' versionCode='([0-9]+)'",out)
    i=re.search(r"(?m)^instrumentation: name='([^']+)' targetPackage='([^']+)'",out)
    if not p: raise DeviceError('APK badging lacks package/version')
    return p.groups(), i.groups() if i else None

def validate(folder: Path):
    app=find_one(folder,'app-release.apk')
    test=find_one(folder,'app-release-androidTest-signed.apk')
    rec=find_one(folder,'release-receipt.json')
    pair=find_one(folder,'instrumentation-pair.json')
    r=json.loads(rec.read_text()); p=json.loads(pair.read_text())
    if (r.get('schema')!='dustui.m05.release.v1' or p.get('schema')!='dustui.m05.signed-instrumentation-pair.v1' or
        p.get('status')!='HOSTED_SIGNED_PAIR_PENDING_DEVICE_TESTS' or
        r.get('source_sha')!=p.get('source_sha') or r.get('github_run_id')!=p.get('github_run_id') or
        p.get('test_classes')!=ALLOWLIST or r.get('signer_sha256')!=EXPECTED_CERT or
        p.get('certificate_sha256')!=EXPECTED_CERT or
        any(x.get('release_qualified') is not False or x.get('device_qualified') is not False for x in (r,p))):
        raise DeviceError('source, status, signing identity or allowlist mismatch')
    if not re.fullmatch(r'[a-f0-9]{40}',str(r.get('source_sha',''))):
        raise DeviceError('invalid source identity')
    if sha(app)!=r.get('apk_sha256') or sha(app)!=p.get('release_apk_sha256') or sha(test)!=p.get('test_apk_sha256'):
        raise DeviceError('APK bytes differ from reviewed GitHub receipts')
    signer(app);signer(test)
    pkg,inst=package_info(app)
    if pkg != (RELEASE_PKG,'1400') or inst is not None:
        raise DeviceError('release app identity wrong')
    test_pkg,test_inst=package_info(test)
    if test_pkg[0]!=TEST_PKG or test_inst != (RUNNER,RELEASE_PKG):
        raise DeviceError('instrumentation target or runner mismatch')
    result={'schema':'dustui.m05.signed-instrumentation-device.v1','status':'SIGNED_PAIR_VERIFIED_NOT_YET_TESTED',
            'source_sha':r['source_sha'],'github_run_id':r['github_run_id'],
            'release_apk_sha256':sha(app),'test_apk_sha256':sha(test),
            'certificate_sha256':EXPECTED_CERT,'test_classes':ALLOWLIST,
            'device_qualified':False,'release_qualified':False,'root_worker':'NOT_RUN',
            'privileged_cleanup':'NOT_RUN','binder_integration':'NOT_RUN',
            'read_only_sqlite':'NOT_RUN','ui_navigation_instrumentation':'NOT_RUN',
            'independently_authenticated_attestation':False}
    return app,test,result

def adb(serial: str,*args,timeout: int=100,allow_failure=False):
    return call(['adb','-s',serial,*args],timeout=timeout,allow_failure=allow_failure)

def run(app: Path,test: Path,result: dict,serial: str,allow_app_replace: bool,allow_test_install: bool):
    if not re.fullmatch(r'[a-zA-Z0-9_.:-]{3,96}',serial):
        raise DeviceError('explicit Android serial required')
    devices=call(['adb','devices']).stdout
    if not any(parts[:2]==[serial,'device'] for line in devices.splitlines()[1:] if (parts:=line.split())):
        raise DeviceError('ADB device missing or unauthorized')
    if adb(serial,'shell','getprop','ro.product.cpu.abi').stdout.strip()!='arm64-v8a' or adb(serial,'shell','getprop','sys.boot_completed').stdout.strip()!='1':
        raise DeviceError('booted ARM64 device required')
    existing=adb(serial,'shell','pm','path',RELEASE_PKG,allow_failure=True).stdout.strip()
    if existing and not allow_app_replace:
        raise DeviceError('release is installed; pass --allow-app-replace to update without data clearing')
    if not allow_test_install:
        raise DeviceError('test package installation requires --allow-test-install')
    cmd=['install','-r'] if existing else ['install','-t']
    if 'Success' not in adb(serial,*cmd,str(app),timeout=190).stdout:
        raise DeviceError('release install/update lacked Success')
    if 'Success' not in adb(serial,'install','-r','-t',str(test),timeout=190).stdout:
        raise DeviceError('test package install/update lacked Success')
    instr=adb(serial,'shell','pm','list','instrumentation').stdout
    identifier=TEST_PKG+'/'+RUNNER
    if not any(identifier in line and '(target='+RELEASE_PKG+')' in line for line in instr.splitlines()):
        raise DeviceError('registered instrumentation not bound to reviewed release package')
    # No privileged test classes or cleanup methods can be invoked.
    output=adb(serial,'shell','am','instrument','-w','-r','-e','class',','.join(ALLOWLIST),identifier,timeout=360).stdout
    failures=('FAILURES!!!','INSTRUMENTATION_FAILED','INSTRUMENTATION_CODE: 0', 'INSTRUMENTATION_STATUS: stack=')
    passed=len(re.findall(r'(?m)^INSTRUMENTATION_STATUS_CODE: -1\s*$',output))
    classes=set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: class=([^\r\n]+)\s*$',output))
    if (any(s in output for s in failures) or not re.search(r'(?m)^INSTRUMENTATION_CODE: -1\s*$',output) or
        passed!=4 or classes!=set(ALLOWLIST)):
        print(output[-5000:],file=sys.stderr)
        raise DeviceError(f'instrumentation did not pass exact four test methods (passes={passed}, classes={sorted(classes)})')
    result.update({'status':'SAFE_RELEASE_INSTRUMENTATION_PASS_OTHER_GATES_PENDING','device_serial':serial,
                   'device_abi':'arm64-v8a','install_status':'PASS',
                   'safe_instrumentation_tests':passed,'safe_instrumentation_classes':sorted(classes),
                   'read_only_sqlite':'PASS','ui_navigation_instrumentation':'POLICY_ONLY_PASS',
                   'go_startup':'PASS','binder_integration':'NOT_RUN','root_worker':'NOT_RUN'})
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=['plan','run'])
    p.add_argument('--artifact-dir',type=Path,required=True)
    p.add_argument('--serial',default='')
    p.add_argument('--allow-app-replace',action='store_true')
    p.add_argument('--allow-test-install',action='store_true')
    p.add_argument('--output',type=Path)
    a=p.parse_args()
    try:
        app,test,report=validate(a.artifact_dir)
        if a.mode=='run':
            if not a.serial or not a.output: raise DeviceError('run requires --serial and --output')
            if a.output.exists() or a.output.is_symlink(): raise DeviceError('cannot overwrite device evidence')
            report=run(app,test,report,a.serial,a.allow_app_replace,a.allow_test_install)
        elif a.allow_app_replace or a.allow_test_install or a.serial:
            raise DeviceError('plan must be strictly read-only')
        if a.output:
            if a.output.exists() or a.output.is_symlink():raise DeviceError('cannot overwrite device evidence')
            a.output.parent.mkdir(parents=True,exist_ok=True)
            a.output.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
        print(json.dumps(report,sort_keys=True))
        return 0
    except (OSError,ValueError,subprocess.CalledProcessError) as exc:
        print('DUSTUI_M05_DEVICE_INSTRUMENTATION_REFUSED: '+str(exc)[:400],file=sys.stderr)
        return 2
if __name__=='__main__':
    raise SystemExit(main())
