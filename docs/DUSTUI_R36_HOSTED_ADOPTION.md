# DustUI R36 consumer adoption — hosted debug-only gate

This is a **separate** Devtool target `dustui_hosted_r36`, not a replacement for
`dustui_android`'s Termux/native, release, device or qualification workflows.
The supplied source archive HEAD is `31c4bb511dd59704c7b9627ab0d4ffc4ec69b4ed`.
The adopted target can be reviewed statically on Termux, but Gradle must run
only on GitHub Actions. Debug APK output is exact and owned:
`android/app/build/outputs/apk/debug/app-debug.apk`.

The R36 Devtool pin is `246f168ebdef6d9b25c194268c810c877d099fa3`.
`scripts/dustui_r36_hosted.py plan` compiles the existing native DAG using
Devtool's `compile_graph` and verifies the explicit selected source/tasks.
`run` exclusively invokes **Devtool's `run_node`**, which recomputes the graph,
checks GitHub run/attempt, re-admits the semantic job, and executes the
reviewed nested Gradle wrapper. No second Gradle executor or shell bypass.

GitHub workflow `.github/workflows/devtool-dustui-r36-hosted-debug.yml` uses
pinned checkout, setup-python, setup-go, setup-java, setup-android,
setup-gradle and upload-artifact actions. It supplies Go 1.23, Java 21,
Android API 36/build-tools 36.0.0 and NDK 28.2.13676358 for this consumer's
Kotlin/Compose + Go arm64 native runtime, as described by tracked source.
No keystore, production signing, device/root, or release path is admitted.

## Source validation, local (no Gradle)

From a clean checked-out commit with pinned Devtool on `PYTHONPATH`:

```sh
python3 tests/test_dustui_r36_hosted.py -v
python3 scripts/dustui_r36_hosted.py plan --root .
```

## GitHub native acceptance

After committing/pushing the adoption overlay to the matching DustUI repo:

```sh
gh workflow run devtool-dustui-r36-hosted-debug.yml -R Mikeyphw/dustui --ref main
gh run list -R Mikeyphw/dustui --workflow devtool-dustui-r36-hosted-debug.yml --limit 5
```

A successful *new* R36 GitHub run plus the uploaded APK and native receipt
is required before claiming real consumer qualification. Do not treat this
offline plan or the existing `devtool-dustui.yml` debug fallback as evidence
of the new R36 path. Keep both lanes until real parity and rollback evidence
are collected. Uploaded bytes aren't remotely authenticated or release-eligible.

## Three existing metadata corrections

`dustui_go.jobs.go_test_all`, `gate_e_adversarial`, and
`runtime_resilience` previously said `read_only=true` while declaring output
directories. R36's strict config parser refused the entire config, even
when selecting a different target. These three entries now set `false`
accurately; their commands, outputs and target/workflows are untouched.
