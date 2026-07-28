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
