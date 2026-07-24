# DustUI Android UI/UX Contract

DustUI is a rooted Android storage workbench. The UI must remain readable on phones before it adds another dial to the cockpit.

## Compact-screen rules

1. Every primary screen must remain usable at 360dp width.
2. Scrollable screens must include enough bottom padding for the final section to clear bottom navigation.
3. Last sections must use readable cards or metric tiles, not tiny trailing `ListItem` rows.
4. Horizontal chip rows must have end padding and must not use long clipped labels.
5. Scan presets must not appear accidentally clipped on phones.
6. Selection counters must be labeled with context, never shown as bare numbers.
7. List and Treemap must be presented as an explicit two-state choice.
8. Top actions must be contextual labels such as Actions, Export, Run, or Options, not a generic Menu label.

## Explorer rules

1. Explorer rows must keep name, path, size, and action access visible at compact width.
2. Current-folder search and whole-scan search must be distinct.
3. Long paths must be selectable and ellipsized only after a useful prefix or nearby label is visible.
4. Treemap must always keep an exact-size list fallback and labels on large blocks.
5. Root metadata must appear in a details sheet, not inside crowded rows.

## Typography rules

1. Use monospace styling only for paths, commands, contexts, and machine values.
2. Body copy must avoid exaggerated letter spacing.
3. Section headers should be short, practical labels.
4. Large summaries should use metric grids instead of tall label/value stacks.
5. Warning and blocker states must not rely on color alone.

## Navigation rules

1. Compact screens use bottom navigation for the five core destinations.
2. Wide screens use the navigation rail.
3. Overflow destinations live behind More only on compact screens.
4. Destination routing must use `DustDestination`, not ad-hoc boolean toggles.


## Session and permanent-deletion safety rules

1. Scan-scoped selections, filters, details, search results, reviews, and analysis must reset when the active scan ID changes.
2. A deletion review must record its scan ID, root path, operation ID, review timestamp, and exact accepted items.
3. Deletion execution must use the accepted items from that review, not rebuild a plan from live selection.
4. Execution must be rejected when the active scan ID or root differs from the review.
5. The review sheet must list every approved path and every blocked path before irreversible confirmation.
6. Review actions must remain visible while the item list scrolls.
7. Every approved path must carry a device, inode, mode, size, modification, entry-count, and tree fingerprint from review to execution.
8. Execution must revalidate fingerprints, reject selected symlinks and filesystem-boundary traversal, and never follow symlinks.
9. Blocked paths are skipped rather than silently promoted; approved paths may proceed after explicit irreversible confirmation.
10. Results must report each top-level path as deleted, partial, or failed and record only bytes actually deleted.
11. Any successful mutation makes the persisted scan stale and must prompt a new scan.
12. Android cleanup must not move files into an app-private quarantine or advertise reclaim before deletion succeeds.

## Back and adaptive-layout rules

1. Back closes deletion review, details, and selection mode before changing destinations.
2. In Explorer, Back navigates to the parent until the scan root is reached.
3. Compact specialized workbenches return to More; primary workbenches return to Scan.
4. Wide mode must consider both width and height.
5. Donut charts stack vertically on compact widths, and metric grids collapse to one column when needed.
6. Package status indicators must wrap rather than overflow.
7. Package navigation must remain searchable and must not silently truncate the available packages.


## Actionable-path rules

1. Every indexed file or directory surfaced outside Explorer must offer a path to details, Explorer reveal, selection, or deletion planning.
2. Overview old-file cards, whole-scan search results, package-owned paths, module-owned paths, treemap rows, and detail sheets must not be inert text.
3. Module overlay conflict targets are informational protected paths; actions must operate on the indexed module source files that produce the conflict.
4. Compact action cards must stack controls instead of compressing the path or overflowing horizontally.
5. Batch selection and single-item deletion must feed the same review and fingerprint pipeline.

## Accessibility rules

1. Interactive chart rows must provide at least a 48dp target and expose button and selection semantics.
2. Every visual treemap block must have a matching accessible action with name and size.
3. Machine paths must remain left-to-right and use readable body-size monospace text.
4. Empty charts must display an explicit empty state rather than synthetic data.
