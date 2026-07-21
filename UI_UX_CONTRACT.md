# DustUI Android Phase 12 + 13: UI/UX/Typography and Explorer Polish

This phase turns the post-roadmap feature stack into a navigable Android workbench instead of a single overloaded analytics feed.

## Phase 12: UI/UX/Typography system

- Replaced boolean shell routing with named `DustDestination` entries.
- Added compact bottom navigation for primary destinations.
- Added wide navigation rail for tablet/foldable/desktop-size layouts.
- Moved specialized workbenches out of the former monolithic Insights scroll:
  - Overview
  - Root search
  - Packages
  - Modules
  - Certification
  - Cleanup
- Added reusable UI primitives:
  - `DustSectionHeader`
  - `PathText`
  - `MetricRow`
  - `StatusPill`
  - `EmptyState`
- Added a custom Material type scale for DustUI.
- Made root paths selectable and copy-friendly where they appear as identity data.

## Phase 13: Explorer UX polish

- Renamed directory filtering to “Current folder search”.
- Added an explicit “Whole scan search” bridge from Explorer to Root search.
- Improved explorer rows:
  - two-line hierarchy
  - trailing size block
  - selection actions separated from details
  - checkbox only shown once selection mode is active
- Replaced fixed-width detail rows with adaptive metric rows.
- Added labels and sizes on large treemap blocks while keeping exact-size legend fallback.
- Kept cleanup behind review and quarantine.

## Contract

DustUI Android screens should follow these rules:

1. No screen routing by unrelated boolean pairs.
2. Top app bar should contain at most one menu entry plus title.
3. Compact navigation uses primary destinations and a More hub.
4. Wide navigation exposes all workbenches directly.
5. Long root paths should be selectable and must not be the only visible action target.
6. Explorer search must state whether it searches the current folder or whole scan.
7. Details must adapt to narrow widths and larger font scales.
8. Treemap must keep a text legend and exact sizes.
9. Certification and cleanup must not be buried inside general analytics.
10. Destructive work remains review-first and reversible through quarantine.
