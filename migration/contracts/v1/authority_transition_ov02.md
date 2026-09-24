# DustUI OV02 authority transition

OV02 introduces the Go state/protocol/SQLite kernel but does not cut Android production behavior over.

Authority state after OV02:

| Domain | Authority after OV02 | Notes |
|---|---|---|
| Android UI and launch | legacy Kotlin/Compose | unchanged |
| Existing Room v3 database | legacy source of production truth | retained as import source only for Go tests/contracts |
| Go SQLite model | non-production durable authority model | schema, state predicates, operation FSM, protocol, and import plan exist |
| New scan writes | legacy Room | no production cutover before OV05 |
| Destructive mutation | legacy contained path | no Go destructive execution before OV08 |

Hard rule retained: no Room/Go dual writer is introduced. Room v3 import is deterministic, one-way, versioned, and explicitly includes retained readable `quarantine_entries` with no new writes.

OV02 deliberately leaves Devtool target-local EXO jobs/workflows to the gate/Devtool work. The overlay does not add unsupported `.devtool.toml` jobs or workflows.
