# REM-05 — Legacy surface and release-truth retirement

REM-05 closes the final OV14 retirement blockers.

- The `dust_tui_legacy` Devtool target is removed; Python is no longer a compatibility runtime target.
- `DustNavigationModel` is retired. `DustShellNavigation` is the adaptive Android shell model and carries no legacy destination authority.
- App-local `ReleasePackaging` and `ReleaseReadiness` truth engines are removed. Generic release qualification is owned by Devtool workflows/evidence.
- Gate D closes only when the source-derived retirement audit reports zero blockers.

This overlay does not run OV15 Gate E; it makes the repository eligible for it.
