# Android Phase 8B: Visual analytics

Phase 8B adds dependency-light Compose visualizations backed by persisted scan data.

- Storage composition donut with exact byte legend and explorer filtering.
- Largest-child bars in the directory explorer with drill-down navigation.
- Snapshot-history line chart.
- Ordered file-age bars.
- Per-app storage footprint donut.
- Semantic chart descriptions and exact textual values remain available for accessibility.
- Small storage categories are folded into an explicit Other slice.

Charts are read-only analysis surfaces. They do not bypass cleanup review, quarantine, or root safety policy.
