# DustUI × Devtool: M03 native Go CI

Canonical entry points: `./devtoolw ci-preflight`, `./devtoolw ci-local`, `./devtoolw go-test`, `./devtoolw go-vet`, `./devtoolw go-build`.
The wrapper is local Go/source only. **Never execute Android Gradle on Termux.**

## Hosted Go (GitHub Actions)

`.github/workflows/devtool-dustui.yml` uses Ubuntu 24.04, immutable Node24-compatible
checkout/setup-python/setup-go/upload-artifact pins, a fixed 40-character Devtool source SHA,
and the reviewed Devtool native DAG. `scripts/dustui_m03_hosted.py` acts only as
an identity/graph guard and delegates actual execution to Devtool
`devtool.actions_graph_export.run_node`. No substitute subprocess Go test command
or second test scheduler exists in the workflow.

The four native nodes are: `test`, `vet`, and `race` as independent roots,
then `build` depending on all three. The Go race lane is **Linux/amd64 with CGO=1**;
it does not run on Termux. All jobs read their toolchain version from `go/go.mod`.
The first hosted acceptance uses `cache: false` to avoid misleading cache reuse;
content caching is deferred to M07 after cold/warm baseline measurement.

Every node rechecks GitHub SHA/run/attempt, immutable Devtool runtime, target-local
reviewed DAG/argv, and graph digest. A passing node emits Devtool typed local
evidence and uploads a best-effort 14-day review artifact. Such metadata **is not
independently authenticated attestation** and proves neither signed release nor device success.

Run on the committed GitHub branch (the overlay itself does NOT push):

```
git push origin HEAD
gh workflow run devtool-dustui.yml -R Mikeyphw/dustui --ref YOUR_BRANCH
gh run list -R Mikeyphw/dustui --workflow devtool-dustui.yml --limit 5
```

A workflow file created only on a non-default branch may not be dispatchable until
the file is present on the default branch. Push/pull_request triggers can be used
for independent acceptance without manual dispatch.

## Android and release remain separate

`.github/workflows/devtool-dustui-r36-hosted-debug.yml` still owns the
reviewed Android debug APK path until M04; it is debug-only and not release-qualified.
M05 will separately define signed release and device acceptance. No Gradle or
release authority is introduced by this Go CI change.
