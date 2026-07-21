# Android Phase 2: Explorer and ownership

Phase 2 turns the Phase 1 scan result surface into a navigable, read-only Android storage explorer.

## Included

- direct-child directory navigation with root-bounded Up behavior
- breadcrumb navigation
- name/path search
- size, name, and modification sorting
- file/directory filters
- rich entry details
- Android package extraction from private, external, OBB, media, and APK paths
- best-effort application label lookup
- storage classification for caches, databases, preferences, APKs, OBB, media, downloads, logs, root modules, system paths, and temporary files
- mount point, filesystem, and read-only state inspection
- complete CSV export through a scoped FileProvider
- a larger persisted explorer window of up to 10,000 entries

The phase remains read-only. No delete, move, chmod, package-data clearing, or arbitrary root shell API is introduced.
