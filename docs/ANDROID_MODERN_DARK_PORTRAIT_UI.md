# Android modern dark portrait UI primitives

Phase 1 adds the shared design primitives for the portrait-mode revamp without changing the route map yet.

## Added primitives

- `DustModernColors`: dark-first Material color tokens for background, flat surface levels, accent actions, informational cyan, warning amber, fatal error, success, and selection docks.
- `DustHeroCard`: top-level scan/storage summary cards.
- `DustTaskCard`: tappable task cards for Home, Clean, Files, and Tools entry points.
- `DustStatusNotice`: success, warning, and error evidence notices that do not rely on color alone.
- `DustMetricTile`: compact label/value tiles for storage, diagnostics, and review summaries.
- `DustActionDock`: bottom action dock for selected cleanup/file items before review.
- `DustFlatIconButton`: compact surface-backed top actions.
- `DustBottomSheetHeader`: standardized modal/bottom-sheet titles and close affordances.
- `DustFilterChips`: horizontally scrollable filter chips.
- `DustPathPill`: compact monospace path token.

## Implementation constraints

- Phase 1 must not change compact navigation membership.
- Phase 1 must not change deletion semantics.
- Phase 1 must not hide root scan diagnostics or replace them with snackbars.
- Later phases should reuse these primitives rather than creating one-off card, chip, status, or dock styles.
- Warning, zero-file, truncation, depth, and fatal-root states must keep explanatory text and evidence paths visible.

## Phase 2: Compact task shell

Phase 2 changes the compact portrait navigation to Home / Files / Clean / Tools while preserving the existing route names internally. Scan remains an internal route and is selected from Home while no scan data exists. Search remains an internal route and highlights Files when active. Advanced destinations are grouped through Tools instead of appearing as additional compact tabs.

The phase also fixes the Cleanup Reports date-window state to use `mutableIntStateOf`, removing the Compose autoboxing warning reported after Phase 1 validation.


## Phase 3: Compact scan sheet

Phase 3 moves scanning out of the compact bottom navigation path and into a persistent top-bar Scan action backed by a modal bottom sheet. Wide layouts continue to keep the full Scan route available, preserving the existing scanner screen and validation surface.

The compact scan sheet must show root identity/access status, scanner backend, SELinux state, common presets, custom absolute-path scanning, path preflight evidence, latest scan warnings, zero-file warnings, truncation/depth notices, and a direct `/data/adb/modules` module-source scan affordance. Fatal preflight failures are evidence notices, not snackbar-only events.

When no scan exists, Home remains Home and displays a scan-required empty state with a Start scan CTA rather than silently selecting the internal Scan route.

## Phase 4 — Home dashboard contract

The compact Home route is now the task-first **Storage health** surface. It must remain available before the first scan and present a Start scan action instead of redirecting to another destination. After a scan, Home must show analyzed bytes, indexed entries, cleanup potential, warning/error counts, diagnostic evidence, best next actions, and storage composition.

Home diagnostics must stay loud: fatal root/preflight failures, suspicious zero-file scans, truncation, depth-limit notices, and stale-after-deletion state cannot collapse into empty values or snackbars. Stale scan state must explicitly lock unsafe cleanup recommendations until a rescan refreshes fingerprints and counts.
