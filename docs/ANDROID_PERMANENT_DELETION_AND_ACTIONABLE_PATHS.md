# Android permanent deletion and actionable paths

This overlay replaces DustUI Android's quarantine-first cleanup model with an irreversible, review-bound deletion pipeline. Historical phase documents still describe the old quarantine implementation; this document and the current Android UI/UX contract supersede that behavior.

## Planning contract

A deletion plan is created only from entries in the active persisted scan. Nested selections are normalized so a selected directory suppresses selected descendants. The privileged service checks the scan root, protected Android paths, root containment, existence, path type, scan modification metadata, selected symlinks, entry-count limits, and filesystem boundaries.

Every approved top-level path returns a fingerprint containing device, inode, mode, stat size, modification time, a deterministic tree digest, and entry count. Blocked items remain visible with their exact reason and are excluded from execution. A mixed plan can proceed with approved items after explicit irreversible confirmation.

## Execution contract

Execution receives only the approved items serialized by the review. It does not rebuild a target list from live selection. The active scan ID and root must still match. Immediately before mutation, the root service recomputes every fingerprint. Changed, missing, protected, symlinked, or boundary-crossing paths are refused.

Directory deletion uses a no-follow tree walk. Nested symlinks are removed as links and are never traversed. Nested mount or filesystem boundaries are skipped. Results are reported per top-level path as `DELETED`, `PARTIAL`, or `FAILED`; only regular-file bytes successfully removed contribute to `deletedBytes`. The operation history keeps aggregate status and partial-failure details.

Any successful filesystem mutation marks the current scan stale. DustUI keeps the old scan visible only as historical evidence and prompts the user to run a new scan before trusting totals.

## Actionable file surfaces

The shared actionable-path card provides batch selection, details, Explorer reveal, and single-item deletion planning. It is used for:

- Overview large old files;
- whole-scan search results;
- package-owned largest paths;
- module-owned largest files;
- module overlay source files;
- Explorer list and treemap entry paths through their existing actions;
- the root metadata/details sheet.

Module conflict target paths under `/system`, `/vendor`, `/product`, `/odm`, or `/system_ext` remain informational. The UI exposes the indexed files inside `/data/adb/modules/...` that contribute those overlays, preventing a decorative system target from masquerading as a deletable item.

## Compatibility

The existing Room `reclaimedBytes` column and legacy quarantine table remain in schema version 2 solely to preserve installed databases. New Android runtime code does not write quarantine records, expose restore, or move selected files into a quarantine directory. The model maps the legacy byte column to `deletedBytes` until a future intentional Room migration renames it.

## Release gate

Devtool must run the Android debug build, unit tests, and lint with zero warnings. The overlay validator additionally checks review binding, fingerprint fields, no-follow deletion, protected-path rules, irreversible confirmation, actionable-path coverage, stale-scan handling, removal of restore from AIDL, and absence of the known unnecessary safe-call patterns.
