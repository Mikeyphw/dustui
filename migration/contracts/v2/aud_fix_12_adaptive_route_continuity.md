# AUD-FIX-12 adaptive route-continuity contract

Status: implemented; Gate D remains open.

1. Layout is derived from semantic route identity and must never mutate `routeKey`, `entityId`, or route generation.
2. Compact uses one content pane; medium may use a supporting pane; expanded uses master/detail for Files, Clean, Apps, Activity, and System.
3. Master selection is route-derived and invokes the same semantic navigation/deep-link model used by compact UI.
4. Resize, rotation, and recreation preserve selected detail identity and cannot admit stale async responses under a new layout.
5. Focus/traversal order is deterministic across pane modes.
6. Charts and treemap expose textual equivalents containing the same values as visual presentation.
7. Destructive cleanup execution remains review-only; adaptive density may not introduce a shortcut.
8. Empty, blocked/stale, failed, loading, and content states remain semantically distinct.
