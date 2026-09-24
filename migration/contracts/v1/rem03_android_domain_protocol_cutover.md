# REM-03 — Android scan/query/history domain cutover

Android no longer instantiates Room as a live scan/query/history or cleanup-metadata authority. `GoDomainStore` is the Android shell adapter and authoritative session, entry, retention, cleanup-operation and cleanup-plan state travels over `dustui.android.runtime.session.v1` to the app-owned Go child process and Go-owned SQLite.

The pre-Go Room v1-v3 database remains only as a bounded compatibility source. `LegacyRoomV3Importer` opens `dustui.db` with `SQLiteDatabase.OPEN_READONLY`, imports historical sessions/entries/cleanup metadata into Go SQLite, and writes its marker only after schema verification succeeds. There is no Room dual-write and no Room fallback read path.

REM-03 preserves the current REM-02 SQLite implementation (`modernc.org/sqlite`) and its WAL/integrity fixes. It does not retire the C++ root scanner or destructive executor; those remain explicit Gate-D blockers for REM-04.

Query execution is also cut over rather than merely persistence: entry lookup, path batches, large-file/extension filters, duplicate buckets/candidates, bounded pagination, and root search execute inside the Go/SQLite runtime. The Android repository no longer implements root search by loading an entire persisted scan and filtering it in Kotlin.
