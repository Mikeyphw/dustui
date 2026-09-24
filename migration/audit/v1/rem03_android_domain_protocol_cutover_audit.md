# REM-03 audit

- Production `Room.databaseBuilder`: retired.
- `ScanRepository`, `ScanForegroundService`, and `StorageAlertNotifier` `ScanDao` calls: retired.
- Android domain adapter: `GoDomainStore` over the bounded Go runtime session protocol.
- Durable authority: Go child process + Go-owned SQLite schema v11.
- Legacy Room history: one-time read-only v1-v3 import only; no dual-write/fallback authority.
- REM-02 persistence retained: `modernc.org/sqlite`, WAL/foreign keys, integrity hotfix, and corrected `root_path` history index.
- Remaining Gate-D blockers: C++ scanner, legacy destructive executor, legacy navigation proof, Python compatibility target, duplicated app-local release truth.
- Query hot paths are Go/SQLite-owned: direct entry lookup, path batches, large-file/extension filters, duplicate grouping/candidates, pagination, and root search; no Kotlin whole-snapshot search fallback remains.
