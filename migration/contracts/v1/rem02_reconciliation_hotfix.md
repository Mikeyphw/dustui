# REM-02 reconciliation hotfix

This hotfix reconciles validation and truth metadata after the already-applied
REM-02 SQLite authority overlays.

It does not replace or reapply SQLite persistence. The production Go runtime
already launches with `serve --db <app-private-path>`, opens Go-owned SQLite,
and reports the persistence authority through readiness.

The REM-01 validator now recognizes the evolved REM-02 process launch contract
and delegates executable Go testing to the existing EXO `job:go_test` instead
of running a duplicate `go test` inside the static contract validator.

The OV14 truth ledger records `go_authority = product-default-qualified` because
both production Android→Go binding and Go SQLite persistence are now observed.
Gate D remains open for the six non-Go retirement blockers. REM-03 remains the
next migration step.
