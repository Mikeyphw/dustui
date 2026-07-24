# Android Phase 18: Uninstalled-App Residue Detection

Phase 18 adds a conservative, evidence-first workbench for package-oriented folders that remain after an application is no longer installed.

## Package visibility

The fixed libsu root interface exposes one dedicated `installedPackages()` operation. It runs only the internal Android commands needed to enumerate users and list packages for each user. Arbitrary shell execution remains absent from AIDL.

A candidate can reach **High confidence** only when:

- Android user discovery completed;
- package listing completed for every discovered user;
- the package is absent from every checked user;
- the location is an exact package-managed root such as `/data/user/<id>/<package>`, `Android/data/<package>`, `Android/media/<package>`, or `Android/obb/<package>`;
- no related installed package suggests shared or migrated ownership.

When visibility is incomplete, every result remains **Uncertain**. Package-query failure is displayed and never interpreted as proof of absence.

## Confidence levels

- **High confidence:** exact managed package directory, package absent across all checked users, no related installed package.
- **Probable:** known backup, export, download, or log location named with an absent package, or a managed location downgraded by related installed packages.
- **Uncertain:** package-shaped shared folder, incomplete user coverage, or any ownership ambiguity.

Only High-confidence, unprotected locations participate in `Select safe` and `Plan safe`. Probable and Uncertain paths require explicit individual action.

## False-positive controls

- Packages installed for any checked user are excluded.
- Related installed packages are shown and reduce confidence.
- `/data/data/<package>` is removed when `/data/user/0/<package>` is also indexed.
- Only exact package-root directory entries are analyzed; descendants do not become duplicate recommendations.
- Package-shaped folder names alone never become High confidence.
- User-protected paths persist and are excluded from recommendations.

## Actions

Every location provides:

- evidence and confidence;
- size, file count, type, and modification time;
- Details;
- Explorer reveal;
- explicit selection;
- permanent-deletion planning;
- Protect this folder / Remove protection.

All deletion flows use the existing scan-bound, fingerprinted, review-first permanent-deletion planner. Phase 18 does not add direct deletion or automatic cleanup.

## Stale data

Any successful deletion marks the scan stale. Residue results remain readable, but no new selection or deletion plan can be created until a fresh scan completes.
