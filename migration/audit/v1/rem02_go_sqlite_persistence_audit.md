# REM-02 audit

Expected post-state:

- Android → Go child-process binding: production.
- Go SQLite implementation: present and opened by the production `serve` path.
- Database location: Android app-private `noBackupFilesDir/go-state/dustui-go.db`.
- Schema: versioned through the Go schema contract.
- Scan-generation replacement: transactional.
- Cleanup mutation journal: durable.
- Room: still present and therefore still a Gate-D blocker.
- Gate D: remains reopened.
