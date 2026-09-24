# OV04 authority transition — query/search/history

OV04 moves DustUI read/query authority toward the Go engine while Android remains intentionally suspended through OV12.

## Newly authoritative in Go

- Paged explorer child listing with generation-bound page tokens.
- Whole-scan search as a distinct operation from current-folder filtering.
- Sort/filter semantics for path/name/logical size/allocated size/modified time.
- Large-file listing with explicit logical versus allocated size semantics.
- Storage summaries and composition/treemap aggregate preparation.
- Scan history/growth series compatibility using OV03 `ScanAuthority` semantics.
- Package-linked path lookup for future app/package cleanup surfaces.
- Activity page preparation with bounded response size.
- Query cancellation and stale-generation failure handling.

## Still not production-authoritative

- Android routes remain diagnostic/reconnect scaffolding only.
- No cleanup, mutation, capability grant, or privileged execution is introduced.
- Room remains a one-way import source only; it does not regain query truth.
- Python/Textual may remain as a reference oracle until its planned retirement, but cannot own production query semantics.

## Android buildability

`android_buildability = "intentionally-suspended"` remains active until OV13. Android test/lint/build failures are not OV04 blockers unless they indicate a new destructive path, hidden fallback, or loss of migration contracts.
