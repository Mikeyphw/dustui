#!/usr/bin/env python3
from __future__ import annotations
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
importer=read('android/app/src/main/kotlin/com/mikeyphw/dustui/data/LegacyRoomV3Importer.kt')
main=read('go/cmd/dustui-go/main.go')+read('go/cmd/dustui-go/runtime_domain.go')
schema=read('go/internal/state/schema.go')
sqlite=read('go/internal/state/sqlite_store.go')
domain=read('go/internal/state/android_domain.go')
gomod=read('go/go.mod'); gradle=read('android/app/build.gradle.kts')
require('Room.databaseBuilder' not in container,'AppContainer still instantiates Room production authority')
require('GoDomainStore(appContext)' in container and 'ScanRepository(appContext, goDomainStore' in container,'AppContainer not wired to GoDomainStore')
for name,text in [('ScanRepository',repo),('ScanForegroundService',service),('StorageAlertNotifier',notifier)]:
    require('ScanDao' not in text and 'dao.' not in text and 'database.scanDao' not in text,f'{name} still uses ScanDao production authority')
for token in [
    'domain.scan.session.upsert','domain.scan.entries.upsert','domain.scan.entries.page',
    'domain.scan.entry.get','domain.scan.entries.by-paths','domain.scan.files.at-least',
    'domain.scan.files.extensions','domain.scan.duplicate.buckets','domain.scan.duplicate.files',
    'domain.scan.search','domain.cleanup.operation.claim','domain.cleanup.plan.replace',
]:
    require(token in store and token in main,f'missing typed Go domain protocol operation: {token}')
require('RootFileSearchEngine.search(domainStore.entriesOnce' not in repo,'root search still reloads the whole Go snapshot into Kotlin')
require('domainStore.rootSearch(scanId, request)' in repo,'root search is not delegated to Go query authority')
require('SQLiteDatabase.OPEN_READONLY' in importer and 'room-v3-imported.marker' in importer,'legacy Room history is not isolated as read-only one-time import')
require('Room.databaseBuilder' not in importer,'legacy importer must not instantiate Room')
require('modernc.org/sqlite' in gomod and '_ "modernc.org/sqlite"' in sqlite and 'sql.Open("sqlite", path)' in sqlite,'REM-02 modernc SQLite authority was regressed')
require('CGO_ENABLED", "0"' in gradle,'Android Go runtime must remain CGO-free')
require('const SchemaVersion = 11' in schema,'REM-03 schema must be v11')
require('scan_sessions(root_path, completed_at_ms, generation)' in schema and 'scan_sessions(canonical_root' not in schema,'scan history index targets wrong column')
for token in ['android_cleanup_operations','android_cleanup_plan_items','preset_id','options_json','scanned_items','error_message']:
    require(token in schema,f'Go domain schema token missing: {token}')
for token in ['UpsertAndroidScanSession','AndroidScanEntriesPage','InterruptActiveAndroidScans','InsertAndroidCleanupOperationIfAbsent','ReplaceAndroidCleanupPlan']:
    require(token in domain,f'Go domain store method missing: {token}')
require('sqliteColumnExists' in sqlite and 'ALTER TABLE scan_sessions ADD COLUMN preset_id' in sqlite,'schema v10 -> v11 column migration missing')
status=json.loads(read('migration/ledger/ov14_legacy_retirement_status.json'))
require(status.get('observed',{}).get('room_domain_authority')=='none','truth ledger does not retire Room authority')
require('room_domain_authority_active' not in status.get('blockers',[]),'Room authority blocker remains in ledger')
require(len(status.get('blockers',[]))==5,'REM-03 should leave exactly five observed retirement blockers')
cfg=tomllib.loads(read('.devtool.toml'))
android=cfg.get('targets',{}).get('dustui_android',{})
jobs=android.get('jobs',{}); flows=android.get('workflows',{})
require(android.get('metadata',{}).get('android_domain_authority')=='go-process+go-sqlite','Devtool metadata does not name Go domain authority')
require(android.get('metadata',{}).get('legacy_room_authority') is False,'Devtool metadata still permits Room authority')
require(android.get('metadata',{}).get('go_sqlite_implementation') is True,'Devtool metadata does not record implemented Go SQLite')
require('rem03_domain_cutover' in jobs,'REM-03 EXO job missing')
for name in ['gate_d','validate']:
    flow=flows.get(name,[])
    require(flow and flow[0]=='job:retirement_gate',f'{name} must remain retirement-gated')
    require('job:rem03_domain_cutover' in flow,f'{name} missing REM-03 cutover validator')
if fail:
    for x in fail: print('REM-03 failure:',x,file=sys.stderr)
    raise SystemExit(1)
print('REM-03 Android Go domain protocol cutover PASS (Room production authority retired; Gate D still open with five blockers)')
