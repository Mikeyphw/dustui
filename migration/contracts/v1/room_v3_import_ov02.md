# Room v3 one-way import contract for OV02

Source Room v3 tables:

- `scan_sessions`
- `scan_entries`
- `cleanup_operations`
- `cleanup_plan_items`
- `quarantine_entries`

Go target tables:

- `scan_sessions`
- `scan_entries`
- `operations`
- `cleanup_manifests`
- `cleanup_results`

Rules:

1. Import is one-way: Room v3 -> Go SQLite.
2. There is no Go -> Room synchronization path.
3. No mapping permits new Room writes after its domain is cut over.
4. `quarantine_entries` remains readable/importable under retention policy only and receives no new writes.
5. Every imported scan/session/operation uses explicit generation and expected-generation predicates.
6. Failed migrations roll back instead of producing partial authority.
