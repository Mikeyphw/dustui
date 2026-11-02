# DustUI Devtool current-state orchestration (M02)

The repository wrapper is `./devtoolw` (`wrapper.schema = 2`). It resolves the
Devtool CLI from the local environment; its default command is `ci-preflight`,
which is source-only and non-mutating. **No Android Gradle task is permitted on
Termux**. Android, signing and package validation belong to GitHub Actions.

## Local source and Go commands

| Command | Purpose |
|---|---|
| `./devtoolw` or `./devtoolw ci-preflight` | Validate source-only Devtool contracts; no build |
| `./devtoolw ci-local` | Run the Go CI DAG (preflight, tests, vet, build) |
| `./devtoolw go-fast` | Go tests + Go vet |
| `./devtoolw go-test` | Go package tests |
| `./devtoolw go-vet` | Go vet |
| `./devtoolw go-build` | Go package build (not an APK) |

The target `dustui_go` runs locally; the target `dustui_hosted_go` is a separate,
reviewed native-Go declaration. **M03 must generate, verify and execute its
GitHub Actions provider pipeline before that target can claim hosted acceptance.**

## GitHub Android debug: temporary R36 lane

`dustui_hosted_r36` remains exactly matched to the existing reviewed R36
hosted workflow (`.github/workflows/devtool-dustui-r36-hosted-debug.yml`). Its
only tasks are `:app:lintDebug` and `:app:assembleDebug`. This is a debug APK
candidate, not an Android unit, device, signed release or attestation gate.
The existing manual GitHub Actions workflows are left untouched in M02.

M04 will replace the R36 integration with a qualified hosted Android source
lane that executes Android unit tests, lint and debug assemble, and will then
retire the R36 target/bridge. M05 owns signed artifacts, real-device execution
and independent release verification.

## Removed migration infrastructure

The former Gate A/B/C history targets, local Android Gradle/Gate D/E DAG,
and AUD-FIX wrappers were removed from `.devtool.toml`. Their archived files
remain in the tree until M06 can delete them without breaking active source,
CI, release or product requirements. M02 does not erase prior releases' signed
or independently verified release receipts.

## Developer verification

```sh
python3 scripts/devtool_actions_contract.py
python3 -m unittest discover -s tests -p test_devtool_m02_contract.py -v
python3 scripts/devtool_m01_contract_check.py
./devtoolw ci-preflight
./devtoolw ci-local
```

The current source-contract checker tests configuration and declarations; a
passing source check is **not** a release or device qualification.
