# DustUI Android Phase 5

Phase 5 adds advanced, read-only storage intelligence on top of the Phase 4 cleanup workflow.

- duplicate candidate grouping with potential-savings estimates
- large old file discovery
- root-module footprint aggregation under `/data/adb/modules`
- bind mount and overlay visibility from `/proc/mounts`
- orphaned app-data summaries
- file-type and age distributions

Duplicate groups are candidates based on equal size and extension. DustUI labels them clearly and does not auto-delete them. Content hashing is deferred to an explicit verification workflow.
