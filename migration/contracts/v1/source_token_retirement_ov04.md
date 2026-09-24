# OV04 source/token retirement

OV04 does not retire Android source-token guards because Android remains intentionally suspended.

It does add behavioral Go tests that replace future UI/source inspections for these invariants:

- full-scan search is not current-folder filtering;
- pagination is generation-bound and stable;
- summaries and large-file lists use explicit logical/allocated size semantics;
- history comparability is semantic, not scan-ID based;
- query cancellation and stale-generation failures are observable behavior.

Any remaining Kotlin/Python source-token guards for search/history must be retired only when Android reconnect or Go TUI parity supplies equivalent behavioral evidence.
