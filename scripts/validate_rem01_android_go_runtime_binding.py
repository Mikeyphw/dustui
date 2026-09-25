#!/usr/bin/env python3
"""AUD-FIX successor-aware REM-01 Android Go runtime binding validator."""
from pathlib import Path
import json, sys, tomllib
ROOT=Path(__file__).resolve().parents[1]
def t(rel):
 p=ROOT/rel; return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''
fail=[]; gradle=t('android/app/build.gradle.kts'); bridge=t('android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt'); main=t('go/cmd/dustui-go/main.go')
for token in ['buildGoAndroidRuntime','libdustui_go.so','GOOS", "android','GOARCH", "arm64','CGO_ENABLED", "0','-buildmode=pie','jniLibs.srcDir']:
 if token not in gradle: fail.append('gradle missing '+token)
for token in ['nativeLibraryDir','ProcessBuilder(','"serve"','"--db"','"go-state"','maxFrameBytes','stale_or_mismatched_runtime_response']:
 if token not in bridge: fail.append('bridge missing '+token)
for token in ['func serve(databasePath string) error','SessionProtocolName','MaxFrameBytes','OpenSQLiteStore(databasePath)']:
 if token not in main: fail.append('server missing '+token)
try:
 state=json.loads(t('migration/qualification/v1/current_state.json')); ledger=json.loads(t('migration/ledger/ov14_legacy_retirement_status.json'))
 obs=state.get('observed_repository',{})
 if not (obs.get('android_go_process_binding_present') and obs.get('android_go_packaging_present')): fail.append('canonical observation does not detect Android Go source binding')
 if ledger.get('qualification_state_source')!='migration/qualification/v1/current_state.json': fail.append('OV14 observation not bound to canonical state')
 if ledger.get('gate_d_observed_status')!=state.get('gate_d',{}).get('status'): fail.append('OV14 Gate-D observation stale')
except Exception as exc: fail.append(f'invalid canonical qualification truth: {exc}')
try:
 cfg=tomllib.loads(t('.devtool.toml')); go_job=cfg['targets']['dustui_android']['jobs']['go_test']; gate=cfg['targets']['dustui_android']['workflows']['gate_d']
 if go_job.get('root')!='../go' or go_job.get('command')!=['go','test','./...']: fail.append('dustui_android go_test EXO job mismatch')
 if 'job:go_test' not in gate: fail.append('Gate D must retain job:go_test')
except Exception as exc: fail.append(f'invalid Devtool Go test job contract: {exc}')
if fail:
 for x in fail: print('REM-01 failure:',x,file=sys.stderr)
 raise SystemExit(1)
print('REM-01 successor-aware Android Go source binding PASS (qualification remains evidence-derived)')
