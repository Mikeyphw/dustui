# Android UI/UX safety overlay

> **Superseded cleanup behavior:** the permanent-deletion overlay replaces the Quarantine action described below. Scan binding, adaptive layout, navigation, and accessibility rules remain active.

This overlay is based on `dustui-20260723-142646.tar.gz` and addresses the Android release blockers and highest-priority compact UX findings.

## Cleanup integrity

Cleanup review now carries the originating scan ID, scan root, operation ID, review timestamp, and exact accepted item list. Execution reconstructs its root-service plan only from this immutable review. A changed active scan invalidates the review before authorization or root execution.

The review sheet exposes all accepted and blocked paths and keeps Cancel and Quarantine actions pinned beneath the scrollable list.

## Session isolation

When the latest scan ID changes, the ViewModel clears Explorer location and filters, selected details, selected paths, cleanup review, root-search results, duplicate verification, package and module reports, and storage analytics. Composable package selection and search state are also keyed to the scan ID.

## Navigation

Android Back follows this order:

1. close cleanup review;
2. close entry details;
3. exit selection mode;
4. navigate Explorer to its parent;
5. return compact specialized workbenches to More;
6. return other destinations to Scan;
7. allow the activity to exit from Scan.

## Compact and accessible UI

- Wide navigation requires sufficient width and height.
- The Explorer chart is optional and selection actions stay pinned to the viewport.
- Storage donuts stack above their legends below 480dp.
- Metric grids switch to one column below 420dp.
- Package status pills wrap in bounded rows.
- Package selection searches the complete package set instead of taking the first 24.
- Result truncation is disclosed in search, modules, conflicts, package paths, and old-file sections.
- Treemap blocks have matching 48dp accessible action rows.
- Interactive chart rows expose button semantics and minimum target sizes.
- Notification permission is requested when starting a scan or enabling scheduled scans, not at application launch.

## Validation

Devtool must run the existing Android target restore, debug build, unit-test, and lint gates. The overlay validator additionally checks review binding, session-reset markers, adaptive layout markers, accessibility markers, manifest integrity, and the exact source base hashes.

## Nullable-session compile guard

Search and Packages routing require an explicit `currentSession != null` branch guard. The earlier no-scan branch protects behavior at runtime, but Kotlin does not propagate that exclusion into later `when` branches. The source contract now locks both guards in place.
