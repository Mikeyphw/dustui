# DustUI Android Phase 4

Phase 4 adds a review-first cleanup workflow. Selected scan entries are normalized to remove nested duplicates, checked against a fixed protected-path policy, revalidated by the privileged service immediately before mutation, and moved into a same-scope `.dustui-quarantine` vault. No permanent-delete API is exposed.

## Safety guarantees

- The scan root itself cannot be selected.
- Critical Android, virtual filesystem, and root-manager control paths are blocked.
- Paths outside the reviewed scan root are rejected.
- Symbolic links are rejected.
- Every target is rechecked immediately before execution.
- Quarantine uses rename/move only; cross-filesystem copy fallback is deliberately absent.
- Restore refuses to overwrite an existing destination.
- Activity and quarantine records are persisted in Room.

## Warning policy

Android lint runs with `warningsAsErrors = true`. Dependency freshness notices are intentionally excluded from feature validation because dependency migrations are performed separately. Root-specific absolute path notices are excluded because root storage analysis depends on those canonical paths. All actionable source, manifest, resource, locale, package visibility, and launcher warnings from the Phase 3 baseline are addressed.
