# DustUI Android Phase 6

Phase 6 hardens automation and interrupted-operation behavior.

- Tasker/Locale-compatible read-only scan and cancel actions
- destructive cleanup and restore blocked while the device is locked
- startup reconciliation of cleanup operations left in EXECUTING state
- explicit interrupted status and recovery message
- release version 0.6.0-phase6

Tasker automation intentionally cannot execute cleanup, restore, or arbitrary root commands.
