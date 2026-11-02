# DustUI × Devtool M05 — restricted release and device acceptance

## M05 — Device import and restricted signed release candidate

The existing M03/M04 jobs remain non-release and retain their GitHub-only
Android build constraint. Release Gradle is **not** eligible for Devtool's
reviewed debug-only Android Actions provider. M05 introduces a separate
`dustui_hosted_release` *native Devtool command job* whose executable is a
fail-closed Python gateway (`scripts/dustui_m05_release_job.py`). The gateway
checks exact GitHub owner, main-branch manual dispatch, source revision,
Linux/x64 host and existing PKCS#12 material before exec'ing the **fixed**
`:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` Gradle tasks.
There is no local wrapper or Termux release Gradle command.

### Configure the signer privately

Create GitHub environment `dustui-release` with **main-only deployment branch
restriction**; optionally require a protection reviewer where supported.
Add these *environment secrets* (never commit them to the public repository):

- `DUSTUI_KEYSTORE_BASE64`: base64 of the EXISTING permanent PKCS#12 keystore.
- `DUSTUI_STORE_PASSWORD`, `DUSTUI_KEY_PASSWORD`, `DUSTUI_KEY_ALIAS`.
- `DUSTUI_EXPECTED_SIGNER_SHA256`: exact SHA-256 of the intended **existing**
  signing certificate (64 hex or colon-delimited). This is verified against
  the keystore before any Gradle execution and against the APK afterward.

For a local keystore, obtain its fingerprint privately using `keytool -list -v`
with the intended alias; do not print or paste signing passwords here.
No secrets are required to apply the overlay or run local contract tests.

After pushing M05 to `main`, explicitly dispatch:

```zsh
gh workflow run devtool-dustui-m05-hosted-release.yml -R Mikeyphw/dustui --ref main
gh run list -R Mikeyphw/dustui --workflow devtool-dustui-m05-hosted-release.yml --limit 5
```

The job requires `workflow_dispatch`, matching GitHub repository/actor/branch,
read-only token and `dustui-release` environment. It never generates a fresh
signer, has no push/PR trigger, and refuses missing or mismatching credentials.
Signed APK and receipt are uploaded without keystore/private signing files.
M05 signs a **candidate** only. A successful GitHub run alone does not qualify
the device, root flows or release.

### Import the exact Android APK on a device (no Gradle)

Download M04 debug APK and inspection JSON from the *same* successful GitHub run.
The M04 APK artifact has a staged `app-debug.apk`, and its evidence artifact has
`apk-inspection.json` (paths may be nested). Import the **exact downloaded APK**:

```zsh
python3 -B scripts/dustui_m05_device.py plan \
  --apk /path/to/app-debug.apk --inspection /path/to/apk-inspection.json
```

For explicit opt-in install/launch on an approved, boot-complete `arm64-v8a`
Android device with `adb` and Android `apksigner`/`aapt2` installed:

```zsh
python3 -B scripts/dustui_m05_device.py run \
  --apk /path/to/app-debug.apk --inspection /path/to/apk-inspection.json \
  --serial EXACT_SERIAL --allow-install \
  --output /path/to/new-device-receipt.json
```

The tool verifies the receipt's exact APK/Go-runtime SHA-256, signer certificate,
package and version before installation, then performs ADB install, package and
launch/process smoke checks. Existing installations are **refused** unless
`--allow-replace` is also passed; the app is never uninstalled or data-cleared.
The tool does not execute privileged cleanup. ADB smoke success intentionally
reports Binder, SQLite integration, UI navigation, instrumentation, rooted
runtime and destructive cleanup safe mode as `NOT_RUN` and leaves
`device_qualified=false` / `release_qualified=false`.

The current M05 release milestone is **partially implemented** pending a real
signed-hosted run and stronger device integration/instrumentation evidence.
Do not publish a release-qualified manifest based only on this workflow.

