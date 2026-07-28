# Android modern dark portrait final seal

This document closes the modern dark portrait UI roadmap after Phases 1–7.

## Scope

Phase 8 is a contract and validation seal. It intentionally avoids adding another user-facing mode. The completed app keeps the four compact task zones, the shared dark primitives, the review-first cleanup model, and the module-source diagnostics introduced by the earlier phases.

## Sealed phase map

| Phase | Sealed behavior |
| --- | --- |
| Phase 1 | Shared dark-first color, shape, card, notice, chip, metric, dock, and bottom-sheet primitives. |
| Phase 2 | Compact Home / Files / Clean / Tools task navigation with route-compatible internal destinations. |
| Phase 3 | Compact Scan opens from the top bar and modal scan/evidence sheet; wide Scan route remains available. |
| Phase 4 | Home becomes Storage health and remains usable before the first scan. |
| Phase 5 | Files separates current-folder filtering from whole-scan search while preserving review-first deletion. |
| Phase 6 | Clean becomes a stale-aware, review-first cleanup cockpit with safe-batch and manual-review boundaries. |
| Phase 7 | Tools groups advanced storage, cleanup, and operations surfaces, with module diagnostics that never collapse wrong-scope scans into empty values. |

## Final compact navigation contract

Compact portrait bottom navigation is exactly:

1. **Home**: `DustDestination.INSIGHTS`, labeled Storage health in the route model.
2. **Files**: `DustDestination.EXPLORER`.
3. **Clean**: `DustDestination.RECOMMENDATIONS`.
4. **Tools**: `DustDestination.MORE`.

`SCAN` is not a compact bottom-tab item. It remains an internal/wide route and is launched from the compact top-bar Scan action. `SEARCH` is not a compact bottom-tab item. It remains the internal whole-scan search route and highlights Files when active.

## Final diagnostics contract

Root, scan, and module diagnostics are evidence surfaces, not decorative status banners. Fatal root/preflight failures, zero-file scans, truncation, depth-limit notices, stale-after-deletion state, and wrong-scope module scans must remain textual and actionable.

Wrong-scope module scans must use the exact wording:

```text
Current scan does not include modules
```

The Modules surface must show the current scan root, required `/data/adb/modules` root, root preflight state, source status, and a **Scan /data/adb/modules** action.

## Final cleanup contract

No modern UI phase may bypass the permanent deletion pipeline. Files, Search, Home, Clean, Tools, module source files, charts, treemaps, recommendation cards, and exact-path sheets must route destructive actions through the scan-bound, fingerprinted, reviewed deletion flow. Stale scans may remain readable, but selection and deletion planning stay locked until a fresh scan refreshes fingerprints and counts.

## Final validation gates

The final Devtool validation for this roadmap should run only the Android tasks that prove the finished UI stack:

```bash
--task :app:assembleDebug \
--task :app:testDebugUnitTest \
--task :app:lintDebug
```

The final accepted run should report zero failed tests, zero errors, and zero warnings. Device/root certification remains part of the release checklist after source validation passes.
