# DustUI Root Android — Phase 1

Phase 1 adds a standalone Android application under `android/` while preserving the existing Python/Textual project.

## Implemented

- libsu `RootService` with a fixed AIDL API and no arbitrary command endpoint.
- Bundled NDK scanner running inside the privileged service process.
- Per-scan cancellation tokens and bounded, validated scan requests.
- Actual depth-limited traversal, hidden-file controls, exclusions, filesystem boundaries, allocated/apparent sizes, and hard-link deduplication.
- Newline-delimited event streaming through an app-owned file descriptor.
- Foreground scan service with progress, cancellation, and completion notifications.
- Room sessions and entries with batched ingestion.
- Shared storage, app data, `/data`, and root-module presets.
- Compose screen for root state, custom paths, active progress, and largest results.
- Unit tests for request validation, event parsing, and incremental stream reading.

## Deliberate boundary

This release is read-only. It does not delete, quarantine, clear caches, or mutate package data. Cleanup planning belongs to Phase 4 after path ownership and safety classification are available.

The bundled scanner is a DustUI-specific native engine rather than an unmodified `du-dust` executable. It preserves the storage-analysis behavior required by Phase 1 while avoiding runtime downloads and shell-command injection.

## Devtool targets

- Repository/default project: Python `dust_tui`
- Android target: `dustui_android`, rooted at `android/`

Validate Android with:

```bash
cd "$HOME/Code/dustui"
devtool --copy --auto-hud --hud-mode desktop-window --yes \
  -r "$HOME/Code/dustui" --target dustui_android \
  apply-overlay "$HOME/Downloads/dustui_root_phase1_overlay.zip" --validate
```

## Root diagnostics repair

Root readiness now verifies the actual privileged `RootService` identity rather than only an interactive shell grant. Every scan receives a root-process directory-open preflight, exact fatal error propagation, persistent warnings, emitted file/directory counts, and visible truncation/depth/filesystem-boundary notices. See `ANDROID_ROOT_SCAN_DIAGNOSTICS.md`.
