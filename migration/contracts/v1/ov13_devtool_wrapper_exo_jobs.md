# DustUI OV13 Devtool wrapper + EXO target-local jobs

This overlay enables DustUI's repository-local Devtool wrapper and adopts the
new supported EXO target-local jobs schema from the uploaded Devtool snapshot.

## Wrapper commands

- `./devtoolw gate-d` → `devtool invoke gate_d --target dustui_android`
- `./devtoolw fast` → `devtool invoke fast --target dustui_android`
- `./devtoolw unit` → `devtool invoke unit --target dustui_android`
- `./devtoolw go-fast` → `devtool invoke fast --target dustui_go`
- `./devtoolw build` / `test` / `lint` → canonical Devtool target commands.

The generated launchers are thin and delegate to `devtool --repo "$repo_root" wrapper exec`.
They do not create a second scheduler, run store, or logging path.

## EXO jobs

DustUI now declares target-local jobs under:

- `[targets.dustui_android.jobs.*]`
- `[targets.dustui_go.jobs.*]`

`[targets.dustui_android.workflows].gate_d` is the canonical Gate D wrapper workflow.
The older OV01 prohibition against `.devtool.toml` jobs/workflows is retired because the
uploaded Devtool snapshot now supports target-local jobs as first-class EXO schema.

## Non-goals

This overlay does not change Android product code, Go runtime behavior, cleanup/mutation
authority, performance budgets, or the pending Termux performance-budget hotfix.
