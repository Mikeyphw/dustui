# M05 — Signed release and device instrumentation (restricted)

M05 run 41504899091 passed 157 release tests and signed the APK with the existing permanent certificate. The first physical-device receipt and four deep-link ADB smoke checks passed; these do **not** qualify Binder/root safety.

The additional GitHub-only workflow `.github/workflows/devtool-dustui-m05-signed-instrumentation.yml` builds the production **release** APK and a **release-targeted AndroidTest** APK from the SAME GitHub commit. The existing GitHub environment `dustui-release` provides the same permanent certificate. The hosted Devtool release target retains ownership of release unit/lint/build tasks. The separate guarded hosted assembly task builds `:app:assembleReleaseAndroidTest` only (the Devtool Android provider remains intentionally debug-only).

The instrumentation APK is re-signed with the same permanent certificate, using existing secrets. No new signing keys or repository signing material are created. Both APKs, their Android package identities and SHA-256 hashes, signatures and `testPackage` target are independently checked. The GitHub artifact stores a source/run-bound pair receipt. No device qualification is claimed on GitHub.

Dispatch from `main` only:

```
gh workflow run devtool-dustui-m05-signed-instrumentation.yml -R Mikeyphw/dustui --ref main
gh run list -R Mikeyphw/dustui --workflow devtool-dustui-m05-signed-instrumentation.yml -L 3
```

Download both APKs and `instrumentation-pair.json` + `release-receipt.json` from the exact **successful** GitHub run, and run the source-bound read-only importer:

```
python3 scripts/dustui_m05_instrumentation_device.py plan --artifact-dir /path/to/download
```

The `run` importer requires an ADB serial and explicit flags `--allow-app-replace --allow-test-install` and a fresh evidence `--output` path; it never uninstalls apps or clears data. Only three classes are selected: `M05ReleaseReadOnlyInstrumentedTest` (one Go/SQLite read), `GoChildLaunchIsolationInstrumentedTest` (one Go startup), and `AdaptiveRouteContinuityInstrumentedTest` (two policy assertions). Four passing test methods, expected class identities, and a successful instrumentation end code are mandatory.

**Excluded:** RootedDestructiveSafetyInstrumentedTest, DeviceScalePerformanceInstrumentedTest, MultiUserResidueValidationInstrumentedTest. No root commands or filesystem cleanups. Binder/root-worker and actual UI-rendering assertions are independent future acceptance gates. Even four passing instrumentation tests do NOT set `device_qualified` or `release_qualified` to true.
