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
## Actionable doughnut-chart rules

1. A storage-composition segment must open the exact indexed files represented by that segment; the chart must not be decorative.
2. Selecting a segment must expose its category name, byte total, percentage, and file count.
3. Each selected category must offer View files, Select all, and Plan deletion actions. Selection and deletion must reuse the reviewed permanent-deletion pipeline.
4. Aggregated Other slices must retain the exact underlying category labels and expose them in a detailed list. They must not merge into an unexplained bucket.
5. Every category file opened from the chart must retain details, Explorer reveal, selection, and single-file deletion planning.
6. The accessible legend must expose category, size, percentage, count, button role, and selected state with at least a 48dp target.
7. After any filesystem mutation, the chart must identify itself as stale. Read-only inspection may remain available, but new chart-driven selection and deletion require a rescan.


## Large-file workbench rules

1. The large-file workbench must operate on the complete active scan entry set, not a truncated Explorer display list.
2. Directories must not appear as large-file deletion candidates.
3. Presets must use explicit size, age, and file-category rules and must never infer safety from size alone.
4. Search must cover file name, full path, and resolved package name.
5. Category filters, sort changes, grouping changes, and preset changes must reset paging to the first page.
6. Paging must be deterministic, bounded, and preserve exact membership without overlap.
7. Group byte totals must reconcile with the exact files visible on the current page.
8. Every surfaced file must support details, Explorer reveal, individual selection, and reviewed permanent-deletion planning.
9. Page-level selection and deletion planning must use only the current visible page and feed the same fingerprinted deletion pipeline.
10. After filesystem mutation, browsing may remain available, but selection and deletion planning must require a fresh scan.

## Exact duplicate-detection rules

1. Equal size, filename, extension, or edge hash must never be presented as proof of duplication. Exact groups require a complete SHA-256 match.
2. Files must be stat-checked after hashing; files that change while hashing are skipped.
3. Paths sharing the same device and inode represent one physical copy. Hard links must not inflate copy counts or reclaimable bytes.
4. Hard-linked and protected physical copies remain visible but are never selected automatically for deletion.
5. Analysis must be bound to one completed scan, persist progress after each size bucket, support pause/resume, and reset when the active scan changes.
6. Every duplicate group must visibly identify the retained physical copy before deletion candidates are offered. Manual mode selects nothing until a retained copy is chosen.
7. Newest, oldest, shortest-path, and preferred-folder strategies are recommendations only; the user can override the retained copy.
8. Duplicate deletion selection must carry the verified SHA-256 into review. The root service must rehash during review and immediately before deletion.
9. A same-size file whose content changed after analysis must be blocked rather than treated as a duplicate.
10. Page and group actions must use exact visible membership and route through the existing scan-bound permanent-deletion planner.
11. Analysis thresholds and page boundaries must be explicit; skipped oversized buckets and unreadable files must be counted rather than hidden.
12. After any filesystem mutation, duplicate analysis is stale and cannot drive new selection or deletion until a fresh scan.

## Uninstalled-app residue rules

1. High-confidence residue requires an exact Android package-oriented directory and proof that the package is absent across every discovered Android user.
2. If user discovery or package visibility is incomplete, all candidates must remain Uncertain and no batch recommendation may be produced.
3. Installed packages are excluded regardless of which checked user owns them.
4. Known export, backup, and log folders may be Probable; package-shaped shared folders remain Uncertain.
5. Related installed packages must be shown and must reduce confidence rather than being ignored.
6. Probable and Uncertain locations are never automatically selected. The user may act only through explicit per-path selection and the normal deletion review.
7. User-protected residue paths persist across launches and are excluded from group and page recommendations.
8. Legacy aliases such as `/data/data/<package>` and `/data/user/0/<package>` must not double-count the same logical location.
9. Every residue location must expose evidence, size, file count, details, Explorer reveal, protection, selection, and reviewed permanent-deletion planning.
10. Residue analysis is bound to the active scan. After filesystem mutation, inspection may continue, but selection and deletion planning require a fresh scan.

