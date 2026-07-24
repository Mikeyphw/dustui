# Android Phase 17 · Exact Duplicate Detection

## Purpose

Phase 17 finds byte-for-byte duplicate files and helps the user choose which physical copies to retain. It does not infer duplicates from names, extensions, timestamps, or equal sizes, and it does not introduce another deletion engine.

## Candidate pipeline

1. Room groups regular files from the active completed scan by exact size.
2. Size buckets with only one file are discarded.
3. The root service hashes the first and last 64 KiB to eliminate obvious nonmatches.
4. Files that still share an edge hash receive a complete SHA-256 pass.
5. Device and inode collapse hard-linked paths into one physical copy.
6. A group is emitted only when at least two distinct physical copies share the complete SHA-256 value.
7. Files are stat-checked after hashing; changed files are skipped.

The analysis covers every same-size bucket at the selected threshold. A single bucket above the bounded IPC path limit is explicitly skipped and counted rather than silently truncated.

## Persistence and cancellation

Analysis snapshots live in the app files directory under `duplicate-analysis/`. The store uses atomic temporary-file replacement and records completed size buckets, verified groups, skipped counts, bytes hashed, and status.

Cancellation records a paused snapshot. Resume continues from the next incomplete size bucket. Results are keyed by scan ID and threshold and are reset when the active scan changes.

## Physical-copy safety

Paths with the same device and inode are one physical copy. Hard-linked copies remain visible but are not automatic deletion candidates because removing one link may reclaim no bytes.

Protected Android paths are also visible but excluded from recommended deletion. They may serve as the retained content anchor while eligible user-storage copies are selected.

## Retained-copy strategies

The workbench supports:

- keep newest;
- keep oldest;
- keep shortest path;
- prefer a user-entered directory;
- choose manually.

Manual mode produces no deletion candidates until the user identifies the retained physical copy. Every recommendation remains reversible before the deletion review is confirmed.

## Content-bound deletion

Duplicate actions carry the verified SHA-256 for every selected path into the existing deletion plan. The root service:

1. rehashes the file during review;
2. rejects content that no longer matches the duplicate group;
3. stores the verified hash in the approved fingerprint;
4. rehashes immediately before deletion;
5. refuses any file whose content or metadata changed.

This closes the same-size replacement gap between analysis, review, and execution.

## UX

The destination is available from More on compact devices and directly from the wide navigation rail. It provides:

- threshold selection;
- run, pause, resume, and restart controls;
- progress and skipped-file metrics;
- query filtering;
- deterministic 12-group pages;
- group and page selection;
- details and Explorer reveal for every path;
- clear hard-link and protected-copy explanations;
- reviewed permanent-deletion planning.

No automatic or background deletion occurs.

## Safety invariants

- Full SHA-256 is required before declaring an exact duplicate.
- At least one physical copy is retained.
- Hard links do not inflate reclaimable bytes.
- Protected paths are never recommended for deletion.
- Content hashes are revalidated at review and execution.
- Stale scan data cannot drive hashing, selection, or deletion.
- All destructive actions reuse the existing permanent-deletion planner.
