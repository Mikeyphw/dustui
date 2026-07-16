# UI/UX Contract

This document is authoritative for the dust-tui terminal interface. New screens, panels, controls, and visual treatments must follow these rules unless this document and the guarding tests are updated in the same change.

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
