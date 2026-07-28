# DustUI final RC release checklist

## Local validation

- [ ] Apply the latest overlay with Devtool validation.
- [ ] Confirm unit tests pass.
- [ ] Confirm Android lint reports 0 warnings and 0 errors.
- [ ] Confirm debug APK builds.
- [ ] Confirm release APK builds.
- [ ] Confirm Room schemas are exported.


## Modern dark portrait final seal validation

- [ ] Confirm compact bottom navigation shows exactly Home, Files, Clean, and Tools.
- [ ] Confirm compact Scan opens from the top action and modal scan/evidence sheet.
- [ ] Confirm Home remains usable before the first scan and shows Storage health after scanning.
- [ ] Confirm Files separates current-folder filtering from whole-scan search.
- [ ] Confirm Clean stays review-first, stale-locked, and fingerprinted before permanent deletion.
- [ ] Confirm Tools groups Storage intelligence, Cleanup workbenches, and Operations.
- [ ] Confirm wrong-scope Modules says `Current scan does not include modules` and offers `Scan /data/adb/modules`.
- [ ] Confirm final Devtool validation runs only `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`, with zero warnings and zero errors.

## Phase 19 ranked cleanup recommendation validation

- [ ] Confirm compact users open Recommendations from More and wide users see it in the navigation rail.
- [ ] Confirm every score shows its size, confidence, safety, and age point breakdown.
- [ ] Confirm verified duplicates retain SHA-256 expectations through review and execution.
- [ ] Confirm only active verified or high-confidence recommendations enter Select safe and Plan safe.
- [ ] Confirm old installers, archives, logs, and large files require explicit recommendation or path review.
- [ ] Confirm overlapping residue directories and child files are not double-counted or selected twice.
- [ ] Confirm every surfaced path supports Details, Explorer, selection, and deletion planning.
- [ ] Confirm Dismiss lasts only for the current scan, Ignore persists, Show hidden reveals both, and Restore reverses either state.
- [ ] Confirm stale scan data blocks recommendation selection and deletion planning while preserving inspection.
- [ ] Confirm Phase 19 completes with 0 warnings and 0 errors.

## Phase 18 uninstalled-app residue validation

- [ ] Confirm Android user discovery and package listing include every available user before any result reaches high confidence.
- [ ] Confirm a package installed for a secondary user is excluded from residue results.
- [ ] Confirm incomplete user/package visibility downgrades every candidate to Uncertain and disables batch recommendations.
- [ ] Confirm managed `/data/user`, `/data/user_de`, `Android/data`, `Android/media`, and `Android/obb` roots can reach High confidence only when the package is absent across checked users.
- [ ] Confirm known backup/export/log roots are at most Probable and package-shaped shared folders remain Uncertain.
- [ ] Confirm related installed packages visibly reduce confidence and prevent automatic recommendation.
- [ ] Confirm `/data/data/<package>` is not double-counted when `/data/user/0/<package>` is present.
- [ ] Confirm probable and uncertain groups are never page- or group-selected automatically.
- [ ] Confirm every location supports Details, Explorer, explicit selection, and reviewed permanent-deletion planning.
- [ ] Protect a residue folder, relaunch, and confirm it remains protected and excluded from recommendations.
- [ ] Remove protection and confirm analysis updates without changing the active scan.
- [ ] Confirm stale scan data blocks residue selection and deletion planning while preserving inspection.
- [ ] Confirm package query failures are visible rather than silently treated as package absence.
- [ ] Confirm Phase 18 completes with 0 warnings and 0 errors.

## Phase 17 duplicate-detection validation

- [ ] Confirm equal-size candidates are edge-hashed and only complete SHA-256 matches become duplicate groups.
- [ ] Modify a file while hashing and confirm it is skipped as changed.
- [ ] Confirm two paths sharing device/inode count as one physical copy and do not inflate reclaimable bytes.
- [ ] Confirm hard-linked and protected copies remain visible but are never automatically selected.
- [ ] Confirm Newest, Oldest, Shortest path, Preferred folder, and Manual strategies retain the intended physical copy.
- [ ] Confirm Manual selects no deletion candidates until a retained copy is chosen.
- [ ] Pause analysis, relaunch the app, resume, and confirm completed size buckets are not rehashed.
- [ ] Change thresholds and confirm persisted results from another threshold are not reused.
- [ ] Confirm group and page selection preserve exact SHA-256 expectations in the deletion plan.
- [ ] Change a duplicate file after analysis but before review and confirm review blocks it.
- [ ] Change a reviewed duplicate file before confirmation and confirm execution blocks it after rehashing.
- [ ] Confirm protected paths are excluded from recommended deletion while remaining visible as copy anchors.
- [ ] Confirm every path supports Details and Explorer reveal.
- [ ] Confirm oversized same-size buckets and unreadable files are counted as skipped rather than silently omitted.
- [ ] Confirm deletion makes duplicate results stale and requires a fresh scan.
- [ ] Confirm duplicate analysis remains responsive, cancelable, and warning-free.

## Phase 16 large-file workbench validation

- [ ] Confirm compact users can open Large files from More and wide users can open it from the rail.
- [ ] Confirm presets return only files matching their documented size, age, and category rules.
- [ ] Confirm search matches file name, full path, and resolved package name.
- [ ] Confirm category changes reset paging and do not show irrelevant categories for the active preset.
- [ ] Confirm Largest, Oldest, Newest, Path, and Package sorts are deterministic.
- [ ] Confirm Category, Directory, and Package grouping preserves exact page membership and byte totals.
- [ ] Confirm Previous and Next never overlap or skip entries.
- [ ] Confirm every row supports Details, Explorer, selection, and permanent-deletion planning.
- [ ] Confirm Select page and Plan page use only the current visible page.
- [ ] Confirm stale scan data blocks selection and deletion planning while preserving read-only inspection.
- [ ] Confirm the workbench uses the complete active scan rather than the Explorer 10,000-row display limit.

## Android deletion and UI/UX safety validation

- [ ] Start a cleanup review, replace the active scan, and confirm the review and selection are cleared.
- [ ] Confirm deletion review lists every approved path and every blocked path before the irreversible confirmation.
- [ ] Confirm Android Back closes review/details, exits selection, navigates Explorer upward, and returns compact More workbenches correctly.
- [ ] Confirm donut charts stack at 360dp and interactive chart rows remain at least 48dp.
- [ ] Tap every storage-composition doughnut segment and confirm it opens the exact indexed files represented by that segment.
- [ ] Confirm selected doughnut categories show bytes, percentage, file count, View files, Select all, and Plan deletion.
- [ ] Confirm the aggregate Other slice lists its exact component categories and never creates duplicate Other legend rows.
- [ ] Confirm TalkBack can select every category from the accessible legend and announces size, percentage, count, and selected state.
- [ ] Delete a file, return to Overview, and confirm chart-driven selection and deletion are disabled until a new scan while read-only viewing remains available.
- [ ] Confirm every treemap block has a keyboard/TalkBack-accessible matching action row.
- [ ] Confirm package search reaches packages beyond the former first-24 limit.
- [ ] Confirm notification permission is requested only when starting background scans or enabling scheduling.
- [ ] Confirm changing or replacing a reviewed file causes execution to refuse that path.
- [ ] Confirm selected symlinks, protected Android paths, scan roots, outside-root paths, and nested filesystem boundaries are blocked.
- [ ] Confirm a mixed plan deletes approved paths, skips blocked paths, and reports partial failures per item.
- [ ] Confirm actual deleted bytes are recorded and the UI requests a rescan after any successful mutation.
- [ ] Confirm old large files, search results, package paths, module source files, Explorer rows, treemap rows, and details sheets expose useful actions.
- [ ] Confirm module conflict actions target module-owned source files rather than protected system overlay targets.
- [ ] Confirm Kotlin and lint complete with 0 warnings, including no unnecessary safe-call warnings.

## Device validation

- [ ] Install the preferred APK on a rooted Android device.
- [ ] Run `android/scripts/install-smoke.sh`.
- [ ] Run `android/scripts/device-certify.sh`.
- [ ] Confirm the app launches without fatal exceptions.
- [ ] Confirm root certification has no failures.
- [ ] Confirm scanner, explorer, treemap, cleanup review, search, packages, modules, and certification screens open.

## Release evidence

- [ ] Run `android/scripts/release-gate.sh`.
- [ ] Run `android/scripts/final-rc-gate.sh` after device evidence is available.
- [ ] Save `android/build/release-candidate/dustui-*/manifest.json`.
- [ ] Save `android/build/release-candidate/dustui-*/SHA256SUMS`.
- [ ] Save `android/build/final-rc/dustui-*/SHIP_STATUS.md`.
- [ ] Attach device-certification archive.

## Ship rule

Ship only when `SHIP_STATUS.md` says `SHIP` or when every warning listed under `SHIP_WITH_WARNINGS` is intentionally accepted.


## Phases 20–22 cleaning workflow validation

### Saved cleanup rules

- [ ] Create include/exclude, extension, size, age, category, package, regex, depth, file/directory, duplicate-only, and residue-confidence rules and verify exact draft-preview membership.
- [ ] Confirm broad and directory rules show warnings and catastrophic regular expressions are rejected.
- [ ] Export rules, import them, and confirm every imported rule starts disabled with a new local ID.
- [ ] Delete every saved rule and confirm the library remains intentionally empty.
- [ ] Confirm protected paths and scan roots appear blocked and cannot enter Select matches or Plan matches.
- [ ] Confirm ancestor/child matches collapse before byte totals and deletion review.
- [ ] Delete a file and confirm stale rule previews remain readable but cannot select or plan until a fresh scan.

### Storage growth history

- [ ] Compare two completed scans and verify new, grown, shrunk, and removed file deltas against source entries.
- [ ] Confirm directory aggregate rows do not double-count growth totals.
- [ ] Confirm category, directory, and package groups preserve exact path totals.
- [ ] Confirm removed paths are Historical only and expose no selection or deletion actions.
- [ ] Mark a row Understood, reveal understood rows, restore it, and confirm the state is comparison-specific.
- [ ] Create disabled cleanup rules from both a current growth path and a ranked recommendation, then preview each before planning deletion.
- [ ] Confirm older/newer comparison navigation never silently changes the latest scan.

### Deletion verification and reports

- [ ] Delete a file successfully and confirm the report records Verified deleted and confirmed bytes.
- [ ] Force post-delete inspection to fail and confirm verification remains unavailable rather than counting the path as deleted.
- [ ] Simulate a path that remains after a positive root-service result and confirm it becomes Partial, not Verified.
- [ ] Change a path after review and confirm the report records Changed since review.
- [ ] Confirm planned, reported, confirmed, and free-space-delta values remain distinct.
- [ ] Export JSON and text reports and verify credential-like message fragments are redacted.
- [ ] Relaunch the app and confirm reports persist and remain readable without an active scan.
- [ ] Start Targeted rescan and confirm it chooses the smallest safe common parent inside the report root through the normal scan flow.
- [ ] Confirm the combined overlay completes all tests and lint with 0 warnings and 0 errors.

## Complete Phases 20–22 qualification

### Saved rules
- [ ] Edit every condition and confirm the draft preview refreshes and lists exact matched and blocked paths.
- [ ] Confirm maximum depth is relative to the include root, not absolute path depth.
- [ ] Confirm unsafe/unbounded expressions are rejected and bounded expressions match correctly.
- [ ] Import rules and confirm they cannot be enabled before opening and saving them as reviewed.
- [ ] Delete every rule and confirm the library remains empty.
- [ ] Confirm recommendation and latest-scan growth conversions create disabled rules.

### Storage history
- [ ] Select arbitrary current and previous completed-scan endpoints.
- [ ] Confirm category, directory, package, extension, module, and mount grouping totals.
- [ ] Confirm timeline chart points and the accessible timeline list select the same scans.
- [ ] Verify new-large, threshold-crossing, directory, log/cache, module, and mount insights.
- [ ] Select a historical current endpoint and confirm all live-file, rule, deletion, Explorer, and rescan actions are disabled.
- [ ] Apply snapshot retention and confirm old entries are pruned before sessions.

### Verification reports
- [ ] Verify planned, attempted, reported, confirmed, partial, and failed bytes independently.
- [ ] Force inspection failure and confirm it remains verification unavailable with zero confirmed bytes.
- [ ] Retry verification and confirm attempts/timestamps update without attributing unrelated external deletion.
- [ ] Confirm device/filesystem/mount context is persisted and exported.
- [ ] Corrupt a report file and confirm the UI surfaces a load diagnostic.
- [ ] Confirm schema-1 migration and unsupported-schema rejection tests pass.
- [ ] Exercise operation/path, root, date, and status filters.
- [ ] Confirm report-save failure is surfaced in the cleanup result.
- [ ] Confirm targeted rescans use the smallest safe common parent.
- [ ] Complete Android build, unit tests, lint, and source audit with 0 warnings and 0 errors.

## Phase 23 app storage inspector

- [ ] Package totals use leaf files only and reconcile with bucket totals.
- [ ] Active APK/code, databases, preferences, and private state cannot be planned for deletion.
- [ ] Active cache actions open Android app settings.
- [ ] Android owns uninstall confirmation.
- [ ] Eligible shared paths retain details, reveal, protection, selection, and deletion review.
- [ ] Duplicate package paths carry SHA-256 expectations.
- [ ] Protected package paths are excluded.
- [ ] Recommendation ignore/restore is persistent and reversible.
- [ ] Recent growth compares the previous completed scan without making historical paths actionable.
- [ ] Android build, tests, and lint complete with 0 warnings and 0 errors.


## Phase 24 cleaning intelligence final gate

- [ ] Run `android/scripts/cleaning-intelligence-gate.sh` and confirm `qualification.json` reports `ready: true`.
- [ ] Confirm the full unit suite has zero failures and lint has zero warnings and zero errors.
- [ ] Confirm Room schema version 2 is exported and migration 1→2 is explicit and non-destructive.
- [ ] Confirm protected paths, scan roots, outside-root paths, symlinks, mount boundaries, replacement fingerprints, and changed SHA-256 content are refused.
- [ ] Confirm hard links do not inflate reclaimable bytes or enter automatic duplicate selection.
- [ ] Confirm incomplete multi-user package visibility and uncertain residue ownership never become safe-batch candidates.
- [ ] Confirm imported rules require review, unsafe regular expressions are rejected, and previews show exact current-scan paths.
- [ ] Confirm historical comparisons remain informational and current actions require the latest completed scan.
- [ ] Confirm unknown verification remains failed/unconfirmed and report exports redact credentials.
- [ ] Confirm interrupted duplicate analysis resumes from completed buckets and interrupted deletion becomes `INTERRUPTED`.
- [ ] Confirm 360dp, large-font, TalkBack, switch, chart/list, and treemap accessibility contracts pass.
- [ ] Confirm the 10,000-file qualification workload completes within the configured fifteen-second budget.
- [ ] Confirm every surfaced current file remains actionable and no UI screen calls `executeCleanup` directly.
- [ ] Confirm the active source audit reports zero quarantine runtime references.
- [ ] Confirm RC packaging includes `cleaning-intelligence-qualification.json`.
- [ ] Confirm `final-rc-gate.sh` returns `DO_NOT_SHIP` when cleaning evidence is missing or failed.

## Debug Workbench release checks

- [ ] Tools exposes Debug workbench before a scan exists.
- [ ] Live state shows destination, layout, scan/root state, stale lock, selected paths, review sheet, root backend, preflight, scan diagnostics, and module diagnostics.
- [ ] Trace timeline is bounded and filterable.
- [ ] Debug events never bypass stale-scan locks, protected-path blocking, fingerprint checks, or review-first deletion.
- [ ] Safe evidence redaction remains covered by unit tests.

## Debug Workbench Phase 11 checklist

- [ ] Tools → Debug workbench shows Diagnostic probes before the trace timeline.
- [ ] Run all probes records probe-start/probe-finish events in the timeline.
- [ ] `/data` and `/data/adb/modules` probes run dry preflight only.
- [ ] Deletion readiness probe explains stale scan and empty selection without executing deletion.
- [ ] Zero-file scan probe makes suspicious empty completed scans explicit.
- [ ] Module scope probe points users back to `/data/adb/modules` when current scan scope is wrong.


## Debug Workbench Phase 12 release checks

- [ ] Tools → Debug workbench shows Export debug bundle.
- [ ] Safe mode is the default and says raw paths are omitted.
- [ ] Path-aware and Full diagnostic modes warn that paths may be included.
- [ ] Exported bundle opens Android share sheet as a ZIP.
- [ ] Export timeline records success/failure without leaking secrets.
- [ ] No export mode reads or includes file contents.

### Debug Workbench Phase 13 release checks
- [ ] Toggle the UI debug overlay from Tools → Debug workbench.
- [ ] Confirm the overlay reports route, layout, scan, root, stale lock, selection, module status, and event count.
- [ ] Confirm tapping the overlay returns to Debug workbench.
- [ ] Confirm disabled actions explain missing scan, root, stale-lock, or confirmation preconditions.

### Debug Workbench Phase 14 final seal checks
- [ ] Run `python3 android/scripts/validate-debug-workbench-contract.py "$REPO"`.
- [ ] Confirm Tools exposes Debug workbench before first scan.
- [ ] Confirm Live state, Trace timeline, Diagnostic probes, Export debug bundle, and UI debug overlay sections are visible.
- [ ] Confirm Safe export omits raw paths and every export mode excludes file contents.
- [ ] Confirm probes do not execute cleanup, create deletion plans, or mutate scan state.
- [ ] Confirm stale-scan, protected-path, fingerprint, and irreversible-confirmation gates still block destructive paths.
- [ ] Confirm disabled actions explain missing scan, root, stale-lock, or confirmation preconditions.
- [ ] Confirm `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass with zero diagnostics warnings and zero errors.

