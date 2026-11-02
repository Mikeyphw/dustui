# DustUI × Devtool: M03 Go / M04 Android hosted CI

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

M04 `.github/workflows/devtool-dustui-m04-hosted-debug.yml` uses pinned GitHub Actions,
a reviewed Devtool runtime and a single native hosted Android job for all three tasks:
`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. The JDK 21,
SDK 36, Build Tools 36, NDK 28.2 and Go SDK are provisioned only on GitHub.
A separate stdlib Python inspector validates the generated APK package ID,
versionCode, v2 debug signer, JUnit testcases/results, ZIP CRC, arm64 PIE Go runtime ELF, and absence
of other native ABIs. Source, graph, APK and runtime hashes are captured as
**local hosted-run evidence**; this is NOT authenticated attestation.

The older R36 workflow and target are retained solely as a debug fallback
until M04 receives an actual passing hosted run. They must NOT be regarded as
unit-test qualified. No Android Gradle tasks are exposed by the local wrapper.
M05 separately establishes signed release and device acceptance gates.

After M04 auto-commit, push the commit and check:

```
git push origin HEAD
gh run list -R Mikeyphw/dustui --workflow devtool-dustui-m04-hosted-debug.yml --limit 5
```

