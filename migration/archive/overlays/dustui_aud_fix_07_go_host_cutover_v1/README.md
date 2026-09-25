# dustui_aud_fix_07_go_host_cutover_v1

AUD-FIX-07 Go CLI/TUI parity and production host cutover.

- Makes the reachable Go CLI/TUI the supported/default host authority.
- Adds real persisted scan/query/search, activity, cleanup, diagnostics, support-export and inspection commands.
- Replaces Render/Smoke-only TUI scaffolding with an interactive loop delegated to the same Go host executor.
- Moves Python/Textual to `migration/oracle/python_textual/` as a non-production, non-default, non-packaged parity oracle retained through AUD-FIX-16.
- Successor-proofs invalidated source-token/host validators and restores Gradle coverage to the normal full validation graph.
- Gate D remains OPEN and Android product-default state remains evidence-controlled.
- Non-gate overlay: apply with `--no-validate`; narrow workflow is `dustui_android#aud_fix_07`.

## Rebase note

Rebased on `dustui-20260925-094937-886956.tar.gz` and the force-applied AUD-FIX-05/06 v3 tree. Original artifact: `dustui_aud_fix_07_go_host_cutover_v1.zip`. Source pins and overlapping payload deltas were regenerated; no Gate R1 pass is inferred from the earlier rolled-back validation run.
