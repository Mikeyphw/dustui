> **AUD-FIX successor-aware:** This is a historical predecessor contract/evidence document. It is not current acceptance authority. Current requirements are indexed by `migration/requirements/v2/requirements.json` and the canonical UI/UX authority copied under `migration/requirements/v2/source/`.

# Android Debug Workbench Final Contract Seal

## Phase 14 — Final debug contract seal

Phase 14 freezes the Debug Workbench roadmap after Phases 9 through 13. It is documentation, validation, and source-contract hardening only; it must not change scan execution, root access, cleanup planning, deletion execution, export content, or navigation behavior.

## Locked roadmap

- **Phase 9 — Debug event model and recorder**: structured `DebugEvent` objects, redacted evidence, and a bounded 500-event in-memory timeline.
- **Phase 10 — Debug Workbench UI**: Tools exposes a scan-independent Debug workbench with Live state and Trace timeline sections.
- **Phase 11 — Diagnostic probes**: evidence-only root, scan, module, cleanup, and search probes that record probe events and never mutate cleanup safety state.
- **Phase 12 — Export bundle**: Safe, Path-aware, and Full diagnostic ZIP exports with report, timeline, probe, scan, root, navigation, cleanup, recommendation, module, and app-info entries.
- **Phase 13 — UI overlay and disabled reasons**: an opt-in floating debug HUD plus plain-language explanations for blocked actions.

## Non-negotiable safety contracts

1. Debug features are diagnostic-only. They must never execute deletion directly, bypass stale-scan locks, bypass protected-path checks, bypass fingerprint validation, or skip review-first permanent deletion.
2. Debug Workbench must remain reachable before the first scan so root and first-run failures can be inspected.
3. Safe export mode must omit raw file paths. Path-aware and Full diagnostic modes may include bounded diagnostic paths, but never file contents.
4. Secret-like values and generic `/data` paths must be redacted before they enter support exports or debug timeline evidence.
5. Probe actions must be manually triggered, evidence-only, and recorded in the debug timeline.
6. The UI debug overlay must be opt-in, dismissible from Debug Workbench, and must not mutate scan, selection, cleanup, or root state.
7. Disabled destructive or privileged actions must explain the missing precondition when the block is caused by scan absence, root unavailability, stale cleanup state, or missing irreversible confirmation.

## Final validation gate

Use the final gate when changing Debug Workbench contracts:

```bash
REPO="$HOME/Code/dustui"

devtool \
  --copy \
  --auto-hud \
  --hud-mode desktop-window \
  --yes \
  -r "$REPO" \
  --target dustui_android \
  apply-overlay "$HOME/Downloads/<debug-final-contract-overlay>.zip" \
  --validate \
  --task :app:compileDebugKotlin \
  --task :app:testDebugUnitTest \
  --task :app:lintDebug
```

The final expected result is zero test failures, zero warnings, and zero diagnostics errors.
