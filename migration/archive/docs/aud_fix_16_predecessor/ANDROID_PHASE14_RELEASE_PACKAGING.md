# DustUI Android Phase 14: Device Smoke + Release Packaging

Phase 14 turns the release candidate into a repeatable evidence bundle instead of a loose APK copied out of Gradle output folders.

## Goals

- Build debug and release APKs through the same release gate used by Devtool.
- Package all APK outputs into a versioned release-candidate directory.
- Record SHA-256 checksums, sizes, signing/installability state, and stable artifact names.
- Provide a device install/launch smoke script that captures evidence before final RC approval.
- Avoid relying on executable bits inside ZIP overlays; scripts are validated with `bash -n`.

## Commands

From `android/`:

```bash
bash scripts/release-gate.sh
```

Create an RC bundle without re-running Gradle after the gate:

```bash
DUSTUI_SKIP_BUILD=1 bash scripts/package-rc.sh
```

Install and launch smoke test a debug APK:

```bash
bash scripts/install-smoke.sh app/build/outputs/apk/debug/app-debug.apk
```

Collect broader device certification evidence:

```bash
bash scripts/device-certify.sh
```

## Outputs

- `build/release-candidate/dustui-<version>/manifest.json`
- `build/release-candidate/dustui-<version>/SHA256SUMS`
- copied APKs named `dustui-<version>-debug.apk`, `dustui-<version>-release-signed.apk`, or `dustui-<version>-release-unsigned.apk`
- `build/install-smoke/` evidence
- `build/device-certification/` evidence plus `build/device-certification.tar.gz`

## Release note

Unsigned release APKs are allowed as RC evidence but are not treated as distribution artifacts. A signed release APK becomes the preferred install artifact when signing is configured.
