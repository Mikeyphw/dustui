# DustUI OV14 Legacy Retirement Contract

OV14 is the post-Gate-D structural purge.  Gate D made the Go engine, Go SQLite,
Go CLI/TUI, and Android semantic shell the product default.  OV14 makes old
production authorities unable to silently return.

Required production status after OV14:

```text
cpp_scanner_runtime = none
python_runtime = none
room_domain_authority = none
legacy_destructive_executor = none
legacy_navigation = none
go_authority = product-default
```

Rules:

- C++ scanner artifacts may remain only as archived fixtures under `migration/archive`.
- Python/Textual may remain only as frozen parity archaeology or source history; it must not be invoked by supported product workflows.
- Room may remain only as one-way import/read-only migration compatibility data; it must not be product domain authority or dual-write state.
- Legacy destructive execution must not be reachable as fallback for cleanup/mutation.
- Legacy Debug/Certification/Deletion Reports top-level routes must not be product navigation destinations.
- Source/token tests are retired only after behavioral or EXO evidence covers the invariant.
- Devtool wrapper and supported target-local EXO jobs are now part of the product evidence path.

This overlay also folds in the OV13 Termux cleaning-performance validator fix: target-local Devtool jobs/workflows are no longer rejected when wrapper schema=1 is present.
