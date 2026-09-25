#!/usr/bin/env python3
"""AUD-FIX successor-aware REM-02 SQLite implementation validator."""
from __future__ import annotations
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def t(rel):
 p=ROOT/rel; return p.read_text(encoding='utf-8',errors='replace') if p.is_file() else ''
fail=[]; go_mod=t('go/go.mod'); store=t('go/internal/state/sqlite_store.go'); main=t('go/cmd/dustui-go/main.go'); kotlin=t('android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt'); ready=t('go/internal/readiness/readiness.go'); tests=t('go/internal/state/sqlite_store_test.go'); schema=t('go/internal/state/schema.go')
state=json.loads(t('migration/qualification/v1/current_state.json')); ledger=json.loads(t('migration/ledger/ov14_legacy_retirement_status.json'))
checks={
 'modernc sqlite driver missing':'modernc.org/sqlite' in go_mod and '_ "modernc.org/sqlite"' in store,
 'database/sql authority missing':'"database/sql"' in store and 'sql.Open(' in store,
 'WAL/schema initialization missing':'SQLitePragmas' in store and 'SchemaVersion' in store,
 'scan history index references wrong column':'scan_sessions(root_path, completed_at_ms, generation)' in schema and 'scan_sessions(canonical_root' not in schema,
 'transactional scan replace missing':'ReplaceScanGeneration' in store and 'BeginTx' in store and 'tx.Commit()' in store,
 'durable cleanup journal missing':'RecordCleanupMutation' in store and 'cleanup_mutation_journal' in store,
 'Android app-private DB binding missing':'"serve",' in kotlin and '"--db",' in kotlin and '"go-state"' in kotlin,
 'serve does not open SQLite authority':'OpenSQLiteStore(databasePath)' in main,
 'readiness persistence authority missing':'PersistenceAuthority' in ready and '"go-sqlite"' in ready,
 'durability/reopen tests missing':'TestSQLiteStoreDurabilityAndSchema' in tests and 'TestCleanupJournalPersistsAcrossReopen' in tests,
 'rollback semantics test missing':'TestReplaceScanGenerationRollsBackOnFailure' in tests,
 'canonical observation does not detect Go SQLite':state.get('observed_repository',{}).get('go_sqlite_implementation_present') is True,
 'OV14 observation does not record Go SQLite':ledger.get('observed',{}).get('go_sqlite_persistence')=='go-owned-sqlite',
 'OV14 observation not canonical':ledger.get('qualification_state_source')=='migration/qualification/v1/current_state.json',
}
for msg,ok in checks.items():
 if not ok: fail.append(msg)
if fail:
 for x in fail: print('REM-02 failure:',x,file=sys.stderr)
 raise SystemExit(1)
print('REM-02 successor-aware Go SQLite implementation PASS (product qualification remains canonical/evidence-derived)')
