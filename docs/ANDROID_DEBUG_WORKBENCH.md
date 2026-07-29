# Android Debug Workbench

## Phase 9 — Debug event model and recorder

DustUI records a bounded in-memory debug timeline for root-aware flows. The recorder is intentionally lightweight and non-persistent in this phase so it cannot become another cleanup artifact.

Contract:

1. Debug events are structured as `DebugEvent` with category, severity, name, message, timestamp, and evidence map.
2. `DebugEventStore` keeps the last 500 events by default.
3. Evidence values are redacted before they enter the store. `/data/adb/modules` remains visible because module-scope diagnostics depend on it; generic `/data/...` paths are shortened.
4. Root, scan, navigation, whole-scan search, deletion review, deletion execution, certification, and insight refresh flows record events.
5. The recorder does not execute cleanup, weaken deletion review, or persist raw filesystem listings.

## Phase 10 — Debug Workbench UI

The Debug Workbench is reachable from Tools and does not require scan data. It is a diagnostic cockpit for understanding what the app currently believes.

Workbench sections:

- Live state: destination, layout, scan state, indexed entries, root state, stale deletion lock, selected count, review sheet state, root backend, SELinux, path preflight, scan diagnostics, and module diagnostics.
- Trace timeline: newest-first event list with category filters and a warning/error filter.
- Timeline control: clear the in-memory buffer without touching scan data, cleanup reports, or root state.

Debug Workbench must remain evidence-only. It can explain disabled or suspicious states, but it must not bypass root preflight, stale-scan locks, protected-path blocking, fingerprint validation, or review-first permanent deletion.

## Phase 11 — Diagnostic probes

The Debug Workbench now includes manual evidence-only probes. A probe is a targeted check that explains one root, scan, module, cleanup, or search state without mutating persisted scan data and without executing deletion.

Probe targets:

- Root service: verifies root binder identity, UID 0, backend, scanner, and SELinux evidence.
- `/data` access: runs a dry scan-root preflight against `/data`.
- `/data/adb/modules` access: runs a dry module-source preflight.
- Active scan state: explains whether the latest scan can feed Home, Files, Clean, and Tools.
- Zero-file scan reason: makes completed empty scans explicit instead of silently presenting zero values.
- Module scope: verifies module values come from `/data/adb/modules` scan evidence.
- Deletion readiness: explains stale-scan, empty-selection, and open-review states without creating or executing a plan.
- Search readiness: checks whether whole-scan search has a scan and indexed entries available.

Every probe result is shown in the workbench and recorded back into the structured debug timeline. Probe evidence is redacted by the same debug-event store rules. Probes must not call cleanup execution or bypass protected-path, stale-scan, fingerprint, or review-first deletion safeguards.


## Phase 12 · Debug export bundle

The Debug workbench now includes an **Export debug bundle** section. The export is a shareable ZIP created under the app cache and sent through Android's normal share sheet. It is diagnostic-only and never executes deletion, starts scans, changes cleanup gates, or reads file contents.

Export modes:

- **Safe**: default support bundle with counts, states, warnings, probe results, scan/root summaries, and redacted roots. Raw paths are omitted.
- **Path-aware**: includes diagnostic paths so scan scope, module scope, and cleanup readiness can be debugged. File contents are never exported.
- **Full diagnostic**: includes bounded path samples and detailed metadata for deep debugging. File contents are still never exported.

Bundle entries:

- `dustui-debug-report.json`
- `trace-events.jsonl`
- `probe-results.jsonl`
- `scan-summary.json`
- `root-diagnostics.json`
- `navigation-state.json`
- `cleanup-state.json`
- `recommendation-summary.json`
- `module-diagnostics.json`
- `app-info.json`

Privacy contract:

1. Safe mode must not include raw file paths.
2. Path-aware and full diagnostic modes may include paths, but never file contents.
3. Secret-like evidence values are redacted in every mode.
4. Export creation must record a structured debug timeline event.
5. Export failure must record a structured error event and surface a user-visible message.

## Phase 13 · UI overlay and disabled-action explanations

Phase 13 adds a diagnostic-only UI debug overlay and explicit disabled-action explanations.

The overlay is toggled from **Tools → Debug workbench → UI debug overlay**. When visible, it floats above the current screen and shows the current destination, compact/wide layout, scan status, root state, stale cleanup lock, selected path count, module diagnostic status, and trace-event count. Tapping the overlay opens the Debug workbench. It does not start scans, execute cleanup, bypass stale locks, or mutate selection state.

Disabled actions must explain why they are blocked when the reason affects support/debuggability. The first locked surfaces are CSV export, device certification, scan start/check access when root is unavailable, stale-scan deletion actions, and final permanent-deletion execution. Messages should tell the user the exact missing precondition, such as starting a scan, retrying root diagnostics, rescanning after cleanup, or confirming irreversible deletion.

## Phase 14 · Final debug contract seal

Phase 14 seals the Debug Workbench roadmap after Phases 9 through 13. It adds final documentation, source contracts, and a source audit script without changing runtime scan, root, cleanup, export, or navigation behavior.

Final contract:

- Debug workbench remains reachable from Tools before scan data exists.
- The timeline remains bounded to 500 in-memory events and redacts sensitive evidence before storage.
- Diagnostic probes remain evidence-only and must not create deletion plans, execute cleanup, or mutate scan state.
- Export bundles keep Safe as the default mode, never include file contents, and keep path exposure mode-specific.
- The UI debug overlay remains opt-in and may only report route/layout/scan/root/selection/module/event state.
- Disabled-action explanations may clarify missing preconditions, but must not weaken root, stale-scan, protected-path, fingerprint, or irreversible-confirmation gates.

The source audit is `android/scripts/validate-debug-workbench-contract.py`. It is intentionally source-level so Devtool can run it quickly before the heavier Android build/test/lint gates.


## Phase 15 · Scan freeze guard and pre-scan task access

Phase 15 keeps the debug roadmap usable when users report an ANR immediately after scan completion.

- Post-scan storage intelligence refresh runs off the UI thread and cancels stale refresh jobs when a newer scan appears.
- Expensive derived pages use background flow contexts instead of recomputing large scan summaries on the input/render thread.
- Files, Clean, Tools, Debug workbench, and deletion reports remain reachable before the first scan. They show explicit no-scan explanations instead of a blank or blocked route.
- Depth-cap diagnostics are informational unless the scan also has a real failure, zero-file warning, truncation, or persisted warning.
- Shared storage, app data, broad data, root module, and custom scan defaults use a deeper traversal cap so normal internal-storage trees do not immediately look broken.
