# OV02 legacy path reachability

OV02 does not remove Android legacy production paths. It adds a Go state/protocol/operation kernel beside them.

Reachable after OV02:

- Kotlin/Compose Android shell.
- Room v3 scan/session/cleanup tables for current production behavior.
- Contained legacy destructive review/execution path from OV01.
- C++ scanner and Kotlin domain engines until their later replacement overlays.

Newly unreachable after OV02:

- None by runtime routing. OV02 is intentionally non-production.

New constraints introduced by OV02:

- Go import contracts are one-way from Room v3 to Go SQLite.
- No Go↔Room dual writer exists or is permitted.
- Legacy `quarantine_entries` is retention/import evidence only and must receive no new writes.
- Corrupt or invalid Go state maps to `recovery_required` evidence, not guessed success.
