# Android Phase 23: App Storage Inspector

Phase 23 upgrades the former Packages summary into a package-centered storage workbench.

## Storage model

DustUI measures leaf files only, avoiding directory/file double counting, and separates:

- APK and split code;
- private app data;
- databases and preferences;
- cache and code cache;
- external app data;
- OBB;
- shared media;
- logs;
- exports and backups;
- other package-owned storage;
- verified duplicate copies;
- high-confidence residue;
- growth since the previous completed scan.

## Safety policy

- Android package-manager code is informational and is never deleted directly.
- Active databases, preferences, and other private state are blocked from direct deletion.
- Active cache and code cache route to Android app settings instead of raw root deletion.
- Shared files, OBB, logs, exports, and residue remain explicit review candidates only.
- Protected folders are excluded from deletion planning.
- Verified duplicate paths retain their expected SHA-256 through deletion review.
- Every destructive action still uses the scan-bound fingerprinted permanent-deletion planner.

## Package actions

- Open Android app settings.
- Request uninstall through Android's package-manager flow.
- Inspect and reveal exact files.
- Select or plan deletion for eligible buckets.
- Protect or unprotect paths.
- Ignore or restore package-specific cleanup recommendations.

## Validation

Phase 23 adds pure-model tests and UI source contracts for leaf-only accounting, growth, protected paths, active-data guards, supported platform actions, filtering, and recommendation state.
