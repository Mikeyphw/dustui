# dustui_aud_fix_08_semantic_navigation_v1

AUD-FIX-08 semantic route, deep-link, restoration, and System decomposition cutover.

- Replaces the legacy 16-destination/layout-remap shell with 27 stable semantic routes shared by Android and Go.
- Adds `dustui://` nested/entity deep links, saveable route/entity/generation restoration, and typed stale/not-found identity handling.
- Makes cleanup candidate/review and Activity run/evidence identities first-class routes.
- Decomposes System into runtime, modules, automation, diagnostics, certification, and settings.
- Canonical qualification now records `legacy_navigation_present=false`; Gate D remains OPEN.
- Non-gate overlay: apply with `--no-validate`, then run `./devtoolw aud-fix-08`.
