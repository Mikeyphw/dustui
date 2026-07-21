# Android Phase 8: verified duplicates and adaptive polish

Phase 8 upgrades duplicate candidates with opt-in root-side SHA-256 verification, adds structured Tasker acceptance results, and surfaces verified reclaimable storage in Insights.

## Safety

Hashing is read-only, bounded to 200 regular non-symlink files per request, and never feeds cleanup automatically. Candidate and verified results remain visually distinct.

## Tasker result extras

Ordered broadcasts receive `dustui_result_status`, `dustui_result_scan_id`, and, for accepted scans, `dustui_result_path`.
