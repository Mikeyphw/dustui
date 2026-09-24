# OV03 Android buildability suspension contract

OV03 formally switches DustUI from coexistence migration to the Go-first Android-suspended migration mode.

Android status for OV03 through OV12: `intentionally-suspended`. Android compile, lint, test, package, install, and release tasks are diagnostic only until OV13 reconnect unless a later overlay explicitly opts a subset back in.

Hard OV03 blockers are:

- Go scanner and ScanAuthority tests;
- size semantic fixtures for logical and allocated bytes;
- sparse-file, hard-link, symlink, truncation, malformed/incomplete, and stale-after-cleanup policy tests;
- semantic history compatibility;
- SQLite/Room import compatibility tests from OV02;
- migration audit-loop validation;
- explicit legacy scan/Room/C++ authority quarantine notes.

Suspension does not permit new destructive reachability. Cleanup execution remains blocked unless the Go ScanAuthority says the scan can drive deletion and later OV08 mutation authority grants a reviewed capability.
