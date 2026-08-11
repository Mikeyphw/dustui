# DustUI Master Remediation Validation Pack

This file is part of the single master remediation overlay. It converts the remaining roadmap gaps into concrete Gradle/device validation commands.

## Required host validation

```bash
cd android
./gradlew --no-daemon --max-workers=1 :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Required Room migration validation

Requires a connected emulator/device because Room `MigrationTestHelper` is an instrumentation API.

```bash
cd android
bash scripts/validate-room-migrations.sh
```

The instrumented suite validates:

- v1 -> v3 via MIGRATION_1_2 + MIGRATION_2_3
- v2 -> v3
- v3 foreign-key cascade behavior for `scan_entries`
- cleanup operation index/schema validation through exported schemas
- abandoned RUNNING sessions migrated to interrupted integrity state

## Required rooted destructive-safety validation

Requires a disposable rooted emulator/device with working `su`.

```bash
cd android
bash scripts/run-rooted-destructive-safety-tests.sh
```

The suite validates:

- fd-relative deletion of the reviewed file
- top-level identity replacement rejection
- symlink leaf rejection
- bind mount inserted below a reviewed directory does not delete mounted contents

## Required multi-user/work-profile residue validation

Requires root. By default this requires at least two Android users/profiles.

```bash
cd android
DUSTUI_REQUIRE_MULTI_USER=1 bash scripts/run-multi-user-residue-validation.sh
```

Optional package override:

```bash
DUSTUI_PACKAGE_UNDER_TEST=com.example.app DUSTUI_REQUIRE_MULTI_USER=1 bash scripts/run-multi-user-residue-validation.sh
```

The suite validates that a package installed for any user/profile suppresses residue classification for that package.

## Required device-scale performance validation

Default: 100,000 generated files.

```bash
cd android
bash scripts/run-device-scale-performance.sh
```

Overrides:

```bash
DUSTUI_SCALE_ENTRIES=250000 DUSTUI_SCALE_MAX_MILLIS=480000 bash scripts/run-device-scale-performance.sh
```

The instrumentation test creates a large dataset in app-private storage, invokes the native scanner, and requires a non-truncated complete scan result.

## Full remediation gate

```bash
cd android
bash scripts/validate-remediation-full.sh
```

To run only host + Room and skip rooted/device checks:

```bash
DUSTUI_SKIP_DEVICE_VALIDATION=1 bash scripts/validate-remediation-full.sh
```

A release candidate should not be considered fully remediation-qualified until the full gate passes on a rooted validation device configured with a secondary user or work profile.
