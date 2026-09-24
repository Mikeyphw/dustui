# dustui_go_ov03_scanner_size_scanauthority_gateA_v1

OV03 / Gate A overlay for the Android-suspended DustUI Go migration.

This overlay implements Go scanner semantics, logical/allocated size truth, ScanAuthority capability decisions, semantic history compatibility, and the formal Android buildability suspension contract for OV03 through OV12.

It intentionally does not add unsupported Devtool target-local jobs/workflows. Canonical EXO gate evidence remains a Gate A external dependency when the installed Devtool runner can provide sealed terminal evidence.
