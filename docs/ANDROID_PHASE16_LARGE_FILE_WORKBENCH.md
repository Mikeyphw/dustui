# Android Phase 16 · Large File Workbench

## Purpose

Phase 16 turns large-file discovery into a dedicated cleaning workspace. It does not create a second deletion system. Every destructive action feeds the existing scan-bound, fingerprinted permanent-deletion planner.

## Data source

The workbench loads the complete active scan through the Go-owned domain store (`GoDomainStore.entriesOnce(scanId)`). It does not reuse the Explorer observation, which is intentionally limited for interactive rendering.

Directories are excluded before filtering and paging.

## Presets

- **100 MiB+**: files at least 100 MiB.
- **500 MiB+**: files at least 500 MiB.
- **1 GiB+**: files at least 1 GiB.
- **Older than 90 days**: files at least 100 MiB and at least 90 days old.
- **Old archives**: archive-category files at least 90 days old.
- **Large videos**: video-category files at least 500 MiB.
- **APK installers**: Android package files, regardless of size.

Presets surface candidates only. They do not claim that a file is safe to delete.

## Filtering and ordering

The workbench supports:

- search by name, full path, or package name;
- category filtering;
- Largest, Oldest, Newest, Path, and Package sorting;
- no grouping, Category, Directory, or Package grouping;
- deterministic 40-item pages.

Category choices are derived from the active preset so irrelevant empty categories are not advertised.

## Actions

Every row supports:

- Details;
- reveal in Explorer;
- batch selection;
- single-file deletion planning.

The current page also offers **Select page** and **Plan page**. Both use exactly the visible page membership. Selection and planning are blocked when scan data is stale after deletion.

## Safety invariants

- No automatic deletion.
- No quarantine or trash detour.
- No directory candidates.
- No bypass around protected paths or scan/root binding.
- No deletion planning from historical or stale scan state.
- No hidden result truncation.
- Group totals reconcile with exact visible files.

## Validation

Phase 16 adds focused tests for preset semantics, query and package matching, category filtering, stable non-overlapping paging, grouping membership, sort order, navigation, and source-level routing into `reviewPaths`.
