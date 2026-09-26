#!/usr/bin/env python3
from __future__ import annotations
import re
import json, sys, tomllib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(rel):
    p=ROOT/rel
    return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''
fail=[]
def require(ok,msg):
    if not ok: fail.append(msg)
container=read('android/app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt')
repo=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt')
service=read('android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt')
notifier=read('android/app/src/main/kotlin/com/mikeyphw/dustui/scan/StorageAlertNotifier.kt')
store=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoDomainStore.kt')
main=read('go/cmd/dustui-go/main.go')+read('go/cmd/dustui-go/runtime_domain.go')
schema=read('go/internal/state/schema.go')
sqlite=read('go/internal/state/sqlite_store.go')
domain=read('go/internal/state/android_domain.go')
gomod=read('go/go.mod'); gradle=read('android/app/build.gradle.kts')
require('Room.databaseBuilder' not in container,'AppContainer still instantiates Room production authority')
require('GoDomainStore(appContext)' in container and 'ScanRepository(appContext, goDomainStore' in container,'AppContainer not wired to GoDomainStore')
for name,text in [('ScanRepository',repo),('ScanForegroundService',service),('StorageAlertNotifier',notifier)]:
    require('ScanDao' not in text and 'dao.' not in text and 'database.scanDao' not in text,f'{name} still uses ScanDao production authority')
def require_operation(label, alternatives):
    ok = any(op in store and op in main for op in alternatives)
    require(ok, f'missing typed Go domain capability {label}; accepted operations={alternatives}')

require_operation('scan-session-upsert', ['domain.scan.session.upsert'])
require_operation('scan-entries-upsert', ['domain.scan.entries.upsert'])
require_operation('bounded-scan-browse', ['domain.scan.browse.page', 'domain.scan.entries.page'])
require_operation('scan-entry-get', ['domain.scan.entry.get'])
require_operation('scan-entries-by-paths', ['domain.scan.entries.by-paths'])
require_operation('scan-files-at-least', ['domain.scan.files.at-least'])
require_operation('scan-files-extensions', ['domain.scan.files.extensions'])
require_operation('duplicate-buckets', ['domain.scan.duplicate.buckets'])
require_operation('duplicate-files', ['domain.scan.duplicate.files'])
require_operation('scan-search', ['domain.scan.search'])
require_operation('cleanup-operation-admission', ['domain.cleanup.operation.claim', 'domain.cleanup.operation.admit'])
require_operation('cleanup-plan-replace', ['domain.cleanup.plan.replace'])
require('RootFileSearchEngine.search(domainStore.entriesOnce' not in repo,'root search still reloads the whole Go snapshot into Kotlin')
require('domainStore.rootSearch(scanId, request)' in repo,'root search is not delegated to Go query authority')
require(not (ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui/data/LegacyRoomV3Importer.kt').exists(),'legacy Room importer remains live after physical retirement')
require(not (ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui/data/DustDatabase.kt').exists(),'DustDatabase remains live after physical retirement')
require(not (ROOT/'android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt').exists(),'ScanDao remains live after physical retirement')
require('androidx.room' not in gradle and 'libs.androidx.room' not in gradle,'Room dependency remains in Android production build')
require('modernc.org/sqlite' in gomod and '_ "modernc.org/sqlite"' in sqlite and 'sql.Open("sqlite", path)' in sqlite,'REM-02 modernc SQLite authority was regressed')
require('CGO_ENABLED", "0"' in gradle,'Android Go runtime must remain CGO-free')
require(re.search(r'const SchemaVersion = (?:1[1-9]|[2-9][0-9]+)', schema) is not None,'REM-03 schema must be v11 or newer')
require('scan_sessions(root_path, completed_at_ms, generation)' in schema and 'scan_sessions(canonical_root' not in schema,'scan history index targets wrong column')
for token in ['android_cleanup_operations','android_cleanup_plan_items','preset_id','options_json','scanned_items','error_message']:
    require(token in schema,f'Go domain schema token missing: {token}')
for token in ['UpsertAndroidScanSession','AndroidScanEntriesPage','InterruptActiveAndroidScans','InsertAndroidCleanupOperationIfAbsent','ReplaceAndroidCleanupPlan']:
    require(token in domain,f'Go domain store method missing: {token}')
require('sqliteColumnExists' in sqlite and 'ALTER TABLE scan_sessions ADD COLUMN preset_id' in sqlite,'schema v10 -> v11 column migration missing')
status=json.loads(read('migration/ledger/ov14_legacy_retirement_status.json'))
state=json.loads(read('migration/qualification/v1/current_state.json'))
require(status.get('qualification_state_source')=='migration/qualification/v1/current_state.json','retirement ledger is not bound to canonical qualification state')
require('gate_d_truth' not in status and 'ov15_status' not in status,'retirement ledger independently declares Gate D/OV15 state')
require(state.get('gate_d',{}).get('status') in {'open','qualified'},'canonical Gate D state missing/invalid')
cfg=tomllib.loads(read('.devtool.toml'))
android=cfg.get('targets',{}).get('dustui_android',{})
jobs=android.get('jobs',{}); flows=android.get('workflows',{})
require(android.get('metadata',{}).get('android_domain_authority')=='go-process+go-sqlite','Devtool metadata does not name Go domain authority')
require(android.get('metadata',{}).get('legacy_room_authority') is False,'Devtool metadata still permits Room authority')
require(android.get('metadata',{}).get('go_sqlite_implementation') is True,'Devtool metadata does not record implemented Go SQLite')
if state.get('ov14',{}).get('status') != 'physically-retired':
    require('rem03_domain_cutover' in jobs,'REM-03 EXO job missing before physical retirement')
    for name in ['gate_d','validate']:
        flow=flows.get(name,[])
        require(flow and flow[0]=='job:qualification_state',f'{name} must begin with canonical qualification state')
        require('job:rem03_domain_cutover' in flow,f'{name} missing REM-03 cutover validator')
else:
    require('aud_fix_16' in flows,'post-retirement successor workflow missing')
if fail:
    for x in fail: print('REM-03 failure:',x,file=sys.stderr)
    raise SystemExit(1)
print('REM-03 successor-aware Android Go domain protocol cutover PASS')
