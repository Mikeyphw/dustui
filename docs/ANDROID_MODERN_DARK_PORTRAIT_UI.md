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

