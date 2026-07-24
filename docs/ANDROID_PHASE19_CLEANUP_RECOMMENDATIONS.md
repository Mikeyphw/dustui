# Android Phase 19: ranked cleanup recommendations

Phase 19 combines the validated large-file, exact-duplicate, and uninstalled-app residue analyzers into one explainable cleaning queue.

## Ranking contract

Every recommendation receives a deterministic score assembled from visible point buckets:

- reclaimable size;
- evidence confidence;
- deletion risk;
- age where available.

The UI shows the exact point breakdown. Ranking never authorizes deletion and never hides the exact current-scan paths.

## Recommendation sources

- exact duplicates verified with complete SHA-256 content hashes;
- high-confidence app residue absent across all discovered Android users;
- large files unchanged for at least 180 days;
- stale APK/APEX installers;
- old large archives;
- oversized old log and text files.

Overlapping paths are de-duplicated. A residue directory, for example, suppresses generic recommendations for files contained below it so reclaimable bytes are not counted twice.

## Batch policy

Only active recommendations with Verified or High confidence and without High risk enter page-level Select safe or Plan safe actions. In this phase that means verified duplicate copies and high-confidence managed residue. Generic age/size heuristics always require explicit recommendation-level or path-level review.

## Reversible state

- Dismiss hides an item only for the current scan.
- Ignore persists across scans.
- Show hidden displays both states and allows Restore.

Hidden recommendations remain auditable and are never silently erased.

## Deletion integration

Every recommendation and every displayed path supports details, Explorer reveal, selection, and permanent-deletion planning. Duplicate recommendations preserve their expected SHA-256 values through review and execution. Filesystem mutation marks the scan stale and blocks new selection until a fresh scan.
