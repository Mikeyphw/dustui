# OV02 source/token test retirement update

OV02 does not delete source/token tests. It adds behavioral Go contract tests that later gates can use to retire token-only coverage safely.

Replacement behavioral coverage added:

- Operation FSM: duplicate request mismatch, late event rejection, terminal stickiness, cancellation stickiness, stale generation rejection, and corrupt-state recovery evidence.
- State schema: required SQLite authority tables, WAL intent, expected-generation predicates, operation event history, and retained legacy quarantine import storage.
- Room import: one-way Room v3 mapping with no new writes and explicit quarantine retention.
- Protocol: versioned envelopes and stable reason codes.

Token-only retirements remain deferred to their mapped overlays and gates.
