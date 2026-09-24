# DustUI OV04 — Query/search/history engine

This overlay adds the Go read/query layer required before UI reconnect:

- generation-bound deterministic pagination;
- distinct whole-scan search and current-folder filtering;
- bounded response size and cancellation;
- large-file, summary, composition, history/growth, package-linked lookup, and activity-page APIs;
- schema index contract for realistic large scans.

Android buildability remains intentionally suspended through OV12. No unsupported Devtool target-local jobs/workflows are introduced.
