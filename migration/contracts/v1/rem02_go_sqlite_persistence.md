# REM-02 — Go-owned SQLite persistence

REM-02 establishes the production Go runtime as the durable SQLite persistence
authority before Android domain consumers are migrated away from Room.

The Android process binding supplies an app-private database path. The Go
runtime opens and migrates that database, exposes its actual persistence
authority in the readiness handshake, persists scan generations transactionally,
and durably journals cleanup mutations.

This remediation intentionally does **not** retire Room yet. REM-03 performs the
Android domain read/write protocol cutover, after which Room can cease being a
production domain authority.
