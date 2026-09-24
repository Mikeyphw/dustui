# OV02 audit loop

Checked against the rechecked roadmap:

- Go package boundaries exist for engine, protocol, state, operations, evidence, scan, query, cleanup, diagnostics, platform, and cmd.
- Typed protocol errors and stable reason codes exist.
- Go SQLite schema contract contains required authority tables, WAL intent, schema versioning, operation event history, expected-generation predicates, and retained quarantine import storage.
- Room v3 import plan covers scan sessions, scan entries, cleanup operations, cleanup plan items, and quarantine entries as one-way import/read-only retention.
- Operation FSM tests cover duplicate request protection, late event rejection, terminal-state stickiness, cancellation stickiness, stale generation rejection, and corrupt state -> recovery evidence.
- No Android production runtime, scanner replacement, destructive execution, or final package/storage model is introduced.
- No unsupported Devtool target-local jobs/workflows are added.
