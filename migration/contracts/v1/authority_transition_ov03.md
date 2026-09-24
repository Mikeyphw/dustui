# OV03 authority transition

OV03 makes Go scanner semantics the migration authority for scan truth. Android production scan cutover still waits for later overlays, but no new Android/Kotlin/C++ scan result may be treated as more authoritative than the Go scanner contract introduced here.

Authority changes:

- Go owns canonical scan identity: canonical root, generation, backend identity, options fingerprint, hidden/exclusion policy, filesystem-boundary policy, timestamps, observed/persisted counts, malformed records, truncation, integrity, source completeness, and stale-after-cleanup state.
- Go owns size semantics: `logicalSizeBytes`, `allocatedSizeBytes`, and `sizeSemanticsUsedForScan`.
- Go owns `ScanAuthority` capability decisions for analysis, comparison, deletion eligibility, duplicate hashing, residue, complete export, and freshness display.
- History compatibility is semantic and does not depend on scan ID equality.
- Android buildability is intentionally suspended through OV12. Android shell paths are reconnect scaffolding only.

Non-authorities that remain reachable as compatibility/reference only:

- Room v3 import source;
- legacy Kotlin repository scan views;
- C++/native scanner source and old NDJSON parser paths;
- Android UI routes that may still display historical data when the app is buildable.

Newly unreachable by contract:

- declaring a scan fresh after cleanup without a new Go generation;
- driving deletion from truncated, malformed, incomplete, or stale scans;
- comparing histories only because scan IDs match;
- presenting Android build failure during OV03-OV12 as accidental when the overlay declares intentional suspension.
