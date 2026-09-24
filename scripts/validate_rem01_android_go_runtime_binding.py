#!/usr/bin/env python3
from pathlib import Path
import json, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
def t(p):
 q=ROOT/p; return q.read_text(errors='replace') if q.is_file() else ''
fail=[]
gradle=t('android/app/build.gradle.kts'); bridge=t('android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt'); main=t('go/cmd/dustui-go/main.go')
for token in ['buildGoAndroidRuntime','libdustui_go.so','GOOS", "android','GOARCH", "arm64','CGO_ENABLED", "0','-buildmode=pie','jniLibs.srcDir']:
 if token not in gradle: fail.append('gradle missing '+token)
for token in ['nativeLibraryDir','ProcessBuilder(executable.absolutePath, "serve")','maxFrameBytes','stale_or_mismatched_runtime_response']:
 if token not in bridge: fail.append('bridge missing '+token)
for token in ['func serve() error','SessionProtocolName','MaxFrameBytes','requiredTables']:
 if token not in main and token != 'requiredTables': fail.append('server missing '+token)
status=json.loads(t('migration/ledger/ov14_legacy_retirement_status.json'))
if status.get('observed',{}).get('android_go_runtime_binding')!='production': fail.append('truth ledger does not record production Android Go binding')
if 'go_android_runtime_binding_missing' in status.get('blockers',[]): fail.append('runtime binding blocker still present')
if status.get('status')!='reopened_blocked': fail.append('REM-01 must not close Gate D')
proc=subprocess.run(['go','test','./...'],cwd=ROOT/'go',text=True,capture_output=True)
if proc.returncode: fail.append('go test failed: '+(proc.stderr or proc.stdout)[-1200:])
if fail:
 for x in fail: print('REM-01 failure:',x,file=sys.stderr)
 raise SystemExit(1)
print('REM-01 Android Go runtime binding PASS; Gate D remains open for remaining retirement blockers')
