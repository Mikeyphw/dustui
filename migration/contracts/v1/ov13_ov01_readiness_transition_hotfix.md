# OV13 OV01 readiness transition hotfix

OV01's Android unit contract originally asserted that the Go readiness target was
present but not authoritative. That assertion was correct for OV01, but it became
stale after OV13 Gate D made the Go CLI/TUI and Android semantic shell the
product default.

This hotfix keeps the repo-root discovery fix and changes only the stale unit
contract so it is migration-phase aware:

- Before Gate D, the test still accepts the OV01 non-authoritative readiness
  flags.
- At/after Gate D, the test requires Go product engine, SQLite/read authority,
  and mutation authority to be true.

No production runtime behavior changes are introduced.
