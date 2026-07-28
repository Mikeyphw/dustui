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


## Modern dark portrait primitive rules

1. The modern portrait shell must use the shared dark-first `DustModernColors` tokens rather than ad-hoc per-screen colors.
2. Primary actions use lime accent containers; informational file/storage affordances use cyan; warnings use amber; fatal root or deletion blockers use error colors.
3. Modern cards are flat surfaces with rounded shapes and no decorative borders.
4. Scan, cleanup, diagnostics, files, and tools surfaces must reuse `DustHeroCard`, `DustTaskCard`, `DustStatusNotice`, `DustMetricTile`, `DustActionDock`, `DustFlatIconButton`, `DustBottomSheetHeader`, `DustFilterChips`, or `DustPathPill` where those primitives fit.
5. Warning and blocker states must remain textual and evidence-backed; color is only a secondary cue.
6. Chip rows must remain horizontally scrollable instead of clipped.
7. Selection and destructive flows must use the action dock only after review-bound selections exist; the dock must not execute deletion directly.

## Navigation rules

1. Compact portrait screens use bottom navigation for the four task zones: Home, Files, Clean, and Tools.
2. Wide screens use the navigation rail and may expose the full workbench list directly.
3. Overflow destinations live behind Tools only on compact screens, while Clean is a primary compact task zone.
4. `SCAN` remains an internal/wide destination; compact scanning opens from the top-bar Scan action and scan/evidence sheet.
5. `SEARCH` remains an internal whole-scan destination; compact selection for search highlights Files.
6. Destination routing must use `DustDestination`, not ad-hoc boolean toggles.


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
3. Compact specialized workbenches return to Tools; Files returns to Home, Clean returns to Home, and whole-scan Search returns to Files. Wide specialized workbenches may return to Scan.
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
## Ranked cleanup recommendation rules

1. Ranking must be deterministic and expose the exact size, confidence, safety, and age point breakdown.
2. Recommendations are candidates only; they must never trigger automatic or background deletion.
3. Every recommendation must show exact current-scan paths and every path must support details, Explorer reveal, selection, and reviewed permanent-deletion planning.
4. Exact duplicate recommendations must preserve expected SHA-256 values through review and immediate pre-deletion revalidation.
5. Only active Verified or High-confidence, non-High-risk recommendations may enter page-level batch actions. Medium and Low-confidence heuristics require explicit per-item review.
6. Overlapping ancestor and descendant paths must not be counted or selected twice across recommendation categories.
7. Dismiss applies only to the active scan; Ignore persists; Show hidden must expose both states and Restore must be available.
8. Hidden recommendations remain auditable and must not disappear from counts without explanation.
9. Recommendation analysis is bound to the active scan. After filesystem mutation, browsing may continue but selection and planning require a fresh scan.
10. Ranking, filtering, paging, and preference changes must preserve exact membership and complete with 0 warnings and 0 errors.



## Saved cleanup rule rules

1. Rules produce current-scan candidates only and never execute deletion automatically or in the background.
2. The editor must update a draft preview after condition changes and expose exact matched paths, bytes, and blocked reasons before saving.
3. Conditions cover include/exclude roots, extensions, minimum/maximum size, age, category, package, safe bounded path expression, relative depth, type, verified duplicates, and residue confidence.
4. Maximum depth is relative to the longest matching include root, or the scan root when no include exists.
5. Path expressions reject unbounded repetition, groups, alternation, lookarounds, and backreferences; matching input and repetition bounds are capped.
6. Imported rules receive new IDs, stay disabled, and cannot be enabled until explicitly opened and saved as reviewed.
7. Broad and directory rules show warnings. Protected paths and the scan root cannot be overridden.
8. Ancestor and descendant matches collapse before byte totals and deletion review.
9. Rules can be duplicated, disabled, deleted, imported, explicitly exported, and created from recommendations or latest-scan growth results.
10. Empty rule libraries remain empty, and stale previews cannot select or plan deletion.

## Storage growth history rules

1. History compares arbitrary completed-scan endpoints using leaf files, never aggregate directory rows.
2. Added, grown, shrunk, and removed paths retain old size, current size, and signed delta.
3. Timeline points show scan dates and totals and have an equivalent accessible action list.
4. Grouping supports category, directory, package, extension, root module, and mount with deterministic totals.
5. Insights include new large files, threshold crossings, fastest-growing directories, sudden log/cache growth, module growth, and mount growth.
6. Understood state is reversible and scoped to the exact scan pair, path, and delta.
7. Snapshot retention is configurable; pruning removes old entries before sessions and keeps the newest bounded set.
8. Only a comparison ending at the newest active scan may expose Details, Explorer, selection, rule creation, deletion planning, or targeted rescans.
9. All older endpoint combinations are informational, even when a row has a current-side entry in that historical snapshot.
10. After deletion, history remains readable but all live-file actions require a fresh scan.

## Deletion verification and report rules

1. Root-service deletion status is not proof; every accepted top-level path is inspected after execution.
2. Missing, present, and verification-unavailable observations remain distinct. Inspection failure never confirms deletion.
3. Reports classify paths as Verified deleted, Partial, Failed, Blocked, or Changed since review.
4. Reports retain planned, attempted, reported, confirmed, partial, and failed bytes separately.
5. Reports retain operation/scan/root/timestamps, fingerprints, verification attempts, app version, device model, SDK, filesystem, mount point/source, and read-only state.
6. Report-save failures are surfaced in the cleanup result. Malformed stored reports remain visible as diagnostics.
7. Schema-1 reports migrate into the current schema and unsupported schemas are rejected.
8. Search and filters cover operation/path, root, date window, and per-status attention states.
9. Users can retry verification, explicitly export redacted JSON/text, and start a normal rescan at the smallest safe common parent.
10. Reports are readable without an active scan, are capped at 100 with deterministic pruning, and cannot trigger deletion directly.


## Phase 23 app storage inspector contract

- The App storage destination must show package-centered, leaf-file-only measurements.
- APK/app code and active internal app state are informational and cannot enter direct deletion planning.
- Active cache and code cache must route to Android app settings.
- Every surfaced eligible file must support details, Explorer reveal, protection, selection, and reviewed permanent-deletion planning.
- Verified duplicate paths must preserve expected SHA-256 values through review.
- High-confidence residue must remain distinct from active app data.
- Uninstall must use Android's package-manager flow.
- Package-specific recommendation ignore state must be reversible and affect the ranked recommendation queue.
- Filesystem mutation must keep app-storage selection and planning disabled until a fresh scan.


## Phase 24 cleaning intelligence qualification contract

- Every current file surfaced by Overview, Explorer, Large files, Duplicates, App residue, Recommendations, Rules, Growth, App storage, charts, or treemap must retain an inspect, reveal, select, or review-planning path.
- No screen may execute permanent deletion directly. All destructive paths pass through the scan-bound reviewed planner.
- Charts must retain a 48dp accessible action row or an equivalent list with button role, selection state, size, percentage, and file count.
- Historical scan comparisons are informational unless the comparison endpoint is the latest active completed scan.
- Active application code and private state remain non-deletable; cache and uninstall use Android-owned flows.
- Missing or unavailable path verification never counts as confirmed deletion.
- The final release gate requires zero warnings, zero errors, exported Room schemas, explicit migrations, process-recovery evidence, 10,000-file scale evidence, and zero active quarantine runtime references.

## Modern dark portrait task shell

The compact portrait shell uses four primary task zones: Home, Files, Clean, and Tools. The route model keeps the existing workbench destinations intact, but compact navigation presents them by user task rather than by historical implementation buckets.

Required compact behavior:

- `INSIGHTS` is presented as Home / Storage health.
- `EXPLORER` is presented as Files.
- `RECOMMENDATIONS` is presented as Clean.
- `MORE` is presented as Tools.
- `SCAN` is not a compact bottom navigation item; compact scan entry is a persistent top action and modal bottom sheet.
- `SEARCH` is not a compact bottom navigation item; it highlights Files when active.
- Specialized workbenches such as rules, growth, large files, duplicates, app residue, package storage, modules, certification, and deletion reports remain reachable through Tools.
- Wide navigation may continue to expose the full workbench list directly.

The compact shell must not weaken cleanup safety, root diagnostics, module-source diagnostics, or stale-scan blocking.



## Modern compact scan sheet contract

- Compact portrait mode must expose Scan as a persistent top-bar action, not a bottom navigation destination.
- Wide mode may continue to expose the full Scan route directly.
- Home with no scan data must remain Home and present a Start scan call to action.
- The scan sheet must show root identity, root backend, scanner availability, SELinux state, and a retry affordance when root is unavailable.
- Custom path checks must surface fatal preflight failures inline with diagnostic code, requested path, canonical path when available, access booleans, and mount information when available.
- Latest scan evidence must preserve zero-file warnings, truncation, depth-limit notices, persisted warnings, and persisted errors.
- `/data/adb/modules` must be available as an explicit module-source scan path so the Modules screen never needs to show silent empty values for a wrong-scope scan.

## Phase 4 — Home dashboard contract

The compact Home route is now the task-first **Storage health** surface. It must remain available before the first scan and present a Start scan action instead of redirecting to another destination. After a scan, Home must show analyzed bytes, indexed entries, cleanup potential, warning/error counts, diagnostic evidence, best next actions, and storage composition.

Home diagnostics must stay loud: fatal root/preflight failures, suspicious zero-file scans, truncation, depth-limit notices, and stale-after-deletion state cannot collapse into empty values or snackbars. Stale scan state must explicitly lock unsafe cleanup recommendations until a rescan refreshes fingerprints and counts.

## Phase 5 — Files and whole-scan search contract

The compact Files route must be the primary place for local folder filtering and whole-scan indexed search. `EXPLORER` remains the file browser, and `SEARCH` remains the internal whole-scan route, but compact selection for `SEARCH` must continue to highlight Files.

Required behavior:

- Files must visibly distinguish **This folder** filtering from **Whole-scan search**.
- Whole-scan search must explain that it queries the persisted scan index, not only the current folder.
- Folder/type chips must be horizontally scrollable and must not replace route-safe sort, chart, or treemap controls.
- Breadcrumbs must remain tappable and left-to-right path text must remain selectable/ellipsized.
- File rows must keep size, modified time, file count, details, selection, and reveal behavior.
- Selection mode must use the shared action dock and must still route to fingerprinted deletion review.
- Search results must reveal back into Files and must not offer direct destructive action without the deletion review sheet.

## Phase 6 — Clean cockpit contract

The compact Clean route must be a review-first cleanup cockpit, not a destructive shortcut.

Required behavior:

- Clean starts with recoverable bytes, active candidate count, hidden count, selected current paths, and safe-batch bytes.
- A visible notice must state that permanent deletion remains review-first and fingerprinted.
- Stale scan state must lock selection and deletion planning while still allowing browsing and inspection.
- Recommended cleanup remains in the Clean route, while Duplicates, Large files, App residue, and Rules chips open their specialized workbenches without changing safety semantics.
- Safe batch selection must be limited to active verified or high-confidence candidates.
- Manual-review candidates must remain labeled as manual review before deletion planning.
- Exact paths must stay in a bottom sheet with Details, Files reveal, Select, and Review actions only; no direct deletion occurs from the recommendation card.

## Phase 7 — Tools hub and module diagnostics contract

The compact Tools route must group advanced surfaces by task while preserving existing internal destinations.

Required behavior:

- Tools must include **Storage intelligence** and **Operations** groups.
- Storage intelligence must include App storage, Root modules, Storage growth, and Whole-scan search.
- Cleanup workbenches must include Cleanup rules, Large files, Duplicates, and App residue.
- Operations must include Deletion reports, Device certification, and Scan diagnostics.
- Whole-scan search remains the internal `SEARCH` destination and compact selection continues to highlight Files.
- Scan diagnostics may open the compact scan/evidence sheet instead of adding a new route.
- Root modules must never show silent empty values for the wrong scan root.
- A wrong-scope module state must show the current scan root, expected `/data/adb/modules` root, root preflight state, source status, and a **Scan /data/adb/modules** action.
- Module overlay conflicts are evidence about mounted targets, but actions apply only to module-owned source files under `/data/adb/modules`.

## Modern dark portrait final qualification contract

Phase 8 seals the completed Home / Files / Clean / Tools stack and removes stale pre-revamp expectations from the release contract.

Required final behavior:

- Phase 1 design primitives are the shared visual vocabulary for modern cards, notices, chips, metric tiles, action docks, and bottom sheets.
- Phase 2 compact navigation exposes exactly Home, Files, Clean, and Tools while preserving historical route names internally.
- Phase 3 compact scan entry is a top-bar Scan action with a modal scan/evidence sheet; root/preflight/zero-file/truncation/depth diagnostics remain textual and actionable.
- Phase 4 Home is Storage health, remains available before the first scan, and keeps diagnostic evidence visible after scans complete.
- Phase 5 Files separates current-folder filtering from whole-scan search and routes destructive paths through the reviewed deletion flow.
- Phase 6 Clean is a review-first cockpit with stale-state locks, safe-batch limits, manual-review labels, and fingerprinted deletion review.
- Phase 7 Tools groups Storage intelligence, Cleanup workbenches, and Operations while preserving App storage, Root modules, Growth, Search, Rules, Large files, Duplicates, App residue, Deletion reports, Certification, and Scan diagnostics.
- Wrong-scope module scans must say `Current scan does not include modules`, show the current root, required `/data/adb/modules` root, root preflight state, source status, and offer `Scan /data/adb/modules`.
- Module overlay conflicts remain evidence about mounted targets; actions apply only to module-owned source files under `/data/adb/modules`.

Final validation gates should include Android debug assembly, debug unit tests, and debug lint with zero warnings and zero errors.

## Debug workbench contract

The Tools task zone includes a scan-independent Debug workbench for source-backed diagnostics. It must expose live state and a structured trace timeline without changing cleanup safety behavior. The debug timeline is bounded, in-memory, filterable by category, and can be cleared without touching scan data, deletion reports, root state, or persisted cleanup settings.

Debug evidence must make root, scan, module-scope, search, recommendation, and deletion-review state visible. It must not silently replace disabled-action explanations, bypass stale-scan locks, or turn protected mounted targets into deletion targets.

### Debug diagnostic probe rules

The Debug Workbench exposes manual diagnostic probes for root service state, `/data` access, `/data/adb/modules` access, active scan health, zero-file scans, module scope, deletion readiness, and whole-scan search readiness. These probes are evidence-only: they may run root preflight checks and read current ViewModel state, but they must not execute deletion, mutate scan data, weaken stale-scan locks, or bypass review-first cleanup.

Probe results must be visible as cards inside Debug Workbench and must also record a structured timeline event with category, severity, message, and redacted evidence.


### Debug export bundle rules

- Debug export is reachable from Tools → Debug workbench and is scan-independent.
- The default export mode is Safe and omits raw paths.
- Path-aware and Full diagnostic export modes may include paths for debugging, but must never include file contents.
- Export bundles must include timeline events, probe results, scan summary, root diagnostics, navigation state, cleanup state, recommendation summary, module diagnostics, and app info.
- Exporting a bundle must not execute deletion, mutate scans, weaken stale-scan locks, or bypass protected-path/fingerprint checks.

### Debug Workbench Phase 13 · UI overlay and disabled actions
- The UI debug overlay is opt-in from Debug workbench and diagnostic-only.
- The overlay must show route/layout/scan/root/stale/selection/module/event state and tapping it must return to Debug workbench.
- Disabled scan, export, certification, stale-deletion, and final deletion actions must expose plain-language reasons instead of silent disabled controls.
- Disabled explanations must not loosen cleanup safeguards, protected-path blocking, fingerprint checks, or stale-scan locks.

## Debug Workbench final contract

Debug Workbench is a Tools-scoped diagnostic surface, not an alternate cleanup path.

Required behavior:

- `DEBUG` must remain reachable through Tools and wide navigation while staying scan-independent.
- The Debug Workbench must show Live state, Trace timeline, Diagnostic probes, Export debug bundle, and UI debug overlay controls.
- Debug events must stay bounded and redacted before storage.
- Probes must remain evidence-only and must not execute cleanup, create deletion plans, bypass stale locks, or bypass protected-path checks.
- Export bundles must default to Safe mode, must never include file contents, and must keep raw paths out of Safe mode.
- The floating UI debug overlay must be opt-in, diagnostic-only, and must route back to Debug Workbench when tapped.
- Disabled-action reason copy may explain blocked actions, but must not convert a blocked action into an executable one.

