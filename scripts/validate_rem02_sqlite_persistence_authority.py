#!/usr/bin/env python3
from __future__ import annotations
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def t(rel): return (ROOT/rel).read_text(encoding="utf-8", errors="replace")
fail=[]
go_mod=t("go/go.mod")
store=t("go/internal/state/sqlite_store.go")
main=t("go/cmd/dustui-go/main.go")
kotlin=t("android/app/src/main/kotlin/com/mikeyphw/dustui/go/GoRuntimeProcess.kt")
ready=t("go/internal/readiness/readiness.go")
tests=t("go/internal/state/sqlite_store_test.go")
checks={
 "modernc sqlite driver missing":"modernc.org/sqlite" in go_mod and '_ "modernc.org/sqlite"' in store,
 "database/sql authority missing":'"database/sql"' in store and "sql.Open(" in store,
 "WAL/schema initialization missing":"SQLitePragmas" in store and "SchemaVersion" in store,
 "scan history index references wrong column":"scan_sessions(root_path, completed_at_ms, generation)" in (ROOT/"go/internal/state/schema.go").read_text(),
 "transactional scan replace missing":"ReplaceScanGeneration" in store and "BeginTx" in store and "tx.Commit()" in store,
 "durable cleanup journal missing":"RecordCleanupMutation" in store and "cleanup_mutation_journal" in store,
 "Android app-private DB binding missing":'"serve",' in kotlin and '"--db",' in kotlin and '"go-state"' in kotlin,
 "serve does not open SQLite authority":"OpenSQLiteStore(databasePath)" in main,
 "readiness does not expose persistence authority":'PersistenceAuthority' in ready and '"go-sqlite"' in ready,
 "durability/reopen tests missing":"TestSQLiteStoreDurabilityAndSchema" in tests and "TestCleanupJournalPersistsAcrossReopen" in tests,
 "rollback semantics test missing":"TestReplaceScanGenerationRollsBackOnFailure" in tests,
}
for msg,ok in checks.items():
    if not ok: fail.append(msg)
if fail:
    for x in fail: print("REM-02 failure:",x,file=sys.stderr)
    raise SystemExit(1)
print("REM-02 Go SQLite persistence authority PASS")
