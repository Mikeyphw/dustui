> **AUD-FIX successor-aware:** This is a historical predecessor contract/evidence document. It is not current acceptance authority. Current requirements are indexed by `migration/requirements/v2/requirements.json` and the canonical UI/UX authority copied under `migration/requirements/v2/source/`.

# UI/UX Contract

This document preserves the predecessor Python/Textual terminal-interface contract as migration evidence. Since AUD-FIX-07 it is not production host authority; the supported Go TUI uses the shared Go product/shell contracts and this file remains a parity reference only until physical oracle retirement.

## Scope

The contract covers typography/topography, layout, compact terminal behavior, Termux display behavior, modal/dialog behavior, and required interaction paths. It applies to the main app, all `ModalScreen` classes, and future feature surfaces.

## Required Layout Behavior

- The app must fit without horizontal overflow at `40x20`, `50x20`, `60x24`, and `80x24` terminal sizes.
- At widths below `95`, the persistent details side pane must be hidden.
- At heights below `34`, the usage chart must be hidden.
- When the details side pane is hidden, selected rows must still be inspectable with the `i` shortcut and a visible `Details` button.
- The results pane must not enforce a fixed minimum width that can exceed narrow terminals.
- New top-level feature areas must either collapse, hide, scroll, or move behind a modal/action at compact sizes.

## Required Modal Behavior

- Every modal screen must use the shared `#dialog` container styling.
- Modal dialogs must fit inside narrow terminals and use internal scrolling for long content.
- Every modal must expose an obvious close/cancel/save action and an Escape binding when dismissal is valid.
- Adding a new `ModalScreen` requires adding it to the modal layout guard test.

## Required Controls

- Icon-free terminal buttons are allowed, but compact buttons must stay narrow enough for phone terminals.
- Numeric settings that map to command flags must be validated before scans are started.
- Settings that map to `dust` flags must be reachable from the TUI settings dialog.
- `dust` JSON mode, full paths, and no-progress output are parser requirements and must not be disabled by settings.

## Typography And Display

- Text must not depend on a single wide layout.
- Long paths and details must be scrollable or inspectable without overlapping neighboring UI.
- Unicode usage bars may be used by default, but an ASCII fallback must remain available for Termux/font compatibility.
- New visual elements must preserve the existing restrained terminal palette and avoid decorative content that reduces scan density.

## Termux Compatibility

- The app must remain usable at phone-like terminal sizes, especially `40x20` and `60x24`.
- Clipboard and opener behavior should prefer Termux commands when available.
- Features that require external binaries must fail gracefully or have a native fallback where practical.

## Regression Guards

The following tests are part of this contract:

- Main layout viewport fit checks across compact and regular terminal sizes.
- Modal viewport fit checks for every `ModalScreen` class.
- Static CSS checks for compact-safe results pane behavior.
- Settings coverage checks for `dust` flag controls.
- ASCII usage bar coverage.

If a new feature changes these expectations, update this document first, then update the tests to encode the new contract.

## AUD-FIX-08 semantic navigation

Navigation identity is semantic and layout-independent. DustUI exposes 27 stable routes grouped under Overview, Files, Clean, Apps, Activity, and System. Compact layouts use a bottom bar and wide layouts use a rail, but both navigate the same route objects; there is no phone/tablet destination substitution.

Entity detail is part of route identity (`files/item/{path}`, `apps/package/{packageName}`, `activity/run/{operationId}`, `activity/evidence/{operationId}`, cleanup candidate/review identities). Missing or stale entity evidence must render a typed problem state instead of silently changing destination. `dustui://` links use the same route keys and are restored through saveable route/entity/generation state.

System is not a generic Tools catch-all: runtime, modules, automation, diagnostics, certification, and settings are separate semantic routes. Scan is a global workflow action rather than a peer route.

## AUD-FIX-09 bound async/state contract

Content routes use a full semantic request binding (`routeKey`, generation, entity kind/id, request ID, optional fingerprint) and the common Loading / Empty / Blocked / Error / Content presentation model. `Blocked` is not an alias for Empty. Risky actions display Go-owned reason codes/messages/remediation actions rather than maintaining a second Android gate policy.

## AUD-FIX-10 — Scan / Overview / Apps truth
- Scan launch is a mandatory `ChooseScope → Preflight → Review → explicit Start` protocol; presets and targeted rescans cannot bypass it.
- Overview exposes scan identity, generation, integrity/freshness, durable operation/activity state, warnings/problems, and separate logical versus allocated bytes. Evidence review routes to Diagnostics.
- Apps labels filesystem totals as measured scan evidence and separately renders package evidence as supported, partial, unavailable, or blocked. Unknown platform evidence is not zero.
- Root, preflight, wrong-scope, integrity, freshness, and unsupported-evidence remediation comes from typed Go action gates.



## AUD-FIX-11 Activity and Diagnostics
Activity is the durable operation history/recovery surface, including cleanup verification evidence and typed Go action gates. System → Diagnostics is the ProblemGraph-backed explanatory surface. Operation correlation is addressable in both directions by semantic Activity route identity. Raw event/probe/debug data is Advanced-only.

## AUD-FIX-12 adaptive layout successor

Phone/tablet differences are chrome and density only. The current semantic route/entity remains authoritative through compact, medium, expanded, rotation, and recreation transitions. Files, Clean, Apps, Activity, and System may expose route-driven supporting/master panes; those panes cannot introduce layout-local destination identity. Accessibility traversal and chart/treemap textual equivalents are part of the same successor contract.

## AUD-FIX-13 acceptance authority

Live UI acceptance is requirement-index-driven through `migration/acceptance/v1/ui_acceptance_matrix.json` and generated `migration/acceptance/v1/current_state.json`. The semantic route tree, route-bound state/action gates, scan/Overview/Apps truth, Activity/Diagnostics, and adaptive/accessibility contracts are the successor evidence. Historical source-token UI validators are archived and non-authoritative. Gate D remains open.
