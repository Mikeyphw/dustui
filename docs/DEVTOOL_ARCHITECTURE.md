# DustUI × Devtool — current product and execution architecture

**Modernization stage:** M0/M1 (product-contract replacement). This document describes the target model and the known remaining work; it is not a release qualification receipt.

## Current product contracts

- `dustui.product.v2`: Go-owned product interface and route/action semantics, emitted by `inspect product`.
- `dustui.kernel.v1`: Go host command protocol; `dustui.sqlite.schema.v1`: persistent database protocol.
- `dustui.android.runtime.session.v1`: Binder/Go Android runtime session interface.
- Go/SQLite owns reads, scans, durable operations, query/search, cleanup authorization and diagnostics. Android and the TUI are consumers of the shared engine.
- The product and readiness JSON outputs **do not** include migration-phase identifiers, historical gate status, source-only qualification claims, or archive/oracle fields.
- A source-side capability contract does **not** demonstrate that an APK was compiled, the Go runtime was packaged, signing is authentic, a device accepted the build, or privileged cleanup is authorized. Those are independent execution gates.

## Ownership map

| Source area | Current product responsibility | Intended Devtool owner |
|---|---|---|
| `go/internal/product` | Go/Android surfaces, semantic routes and action gates | Go tests + source contract |
| `go/internal/readiness`, `go/internal/version` | Supported runtime interface/protocols, not device state | Go source CI |
| `go/internal/tui`, `go/internal/host` | Interactive and machine-readable host flows | Go test/build CI |
| `go/internal/state`, `go/internal/cleanup`, `go/internal/rootworker` | SQLite and safe privileged operations | Go tests/adversarial/device checks |
| `android/app/src/main/.../go` | Kotlin runtime session and route contracts | Hosted Android unit/lint/build |
| `android/app/build.gradle.kts`, `go/cmd/dustui-go` | Hosted ARM64 runtime packaging | Hosted Android with Go toolchain |
| `.devtool.toml`, `devtoolw`, `.github/workflows/` | Build orchestration, DAGs and CI source binding | Devtool + GitHub Actions |
| `migration/`, old gate jobs and archived evidence | Transitional source, not active qualification authority | **Removal after M2–M5 replacement** |

## First overlay: exact replacements

The M01 overlay updates Go `product`, `readiness`, `version`, `engine`, `tui` and their focused tests; updates the Go host inspection test; and replaces Android's old `ProductCutoverContract`/test files with `ProductRuntimeContract`/test files.

No Gradle, release, signing, device, or GitHub Actions configuration is changed by this overlay. Older migration generators and historical evidence remain physically present **only until** their execution/workflow dependencies are deleted in M2–M6. They are no longer imported into current Go product or readiness responses. Do not run those old generators to overwrite the new contract.

## Validation for this stage

1. Exact baseline and touched-file Git identity preflight.
2. Python source-contract negative checks: old JSON fields and Kotlin source names must not reappear.
3. Go package tests and ordinary Go build (`go -C go test ./...`, `go -C go build ./...`) through the overlay validation hook. No Gradle.
4. After pushing, the existing GitHub Actions Android debug lane must still build; separately require the Kotlin `ProductRuntimeContractTest` and Android unit lane before calling M1 Android-side acceptance complete. The current R36 hosted lane does not run unit tests, so a successful R36 artifact alone is insufficient.

## Next stages

M2 replace the migration-heavy `.devtool.toml` and wrapper; M3 source-bound hosted Go provider; M4 hosted Android with unit+lint+assemble; M5 current APK/device/signing verification; M6 remove dead generators, historical receipts, archive scaffolding and obsolete documentation. **Do not preserve historical gate workflows as a long-term compatibility layer.**

## M02 installed: simplified Devtool configuration

`.devtool.toml` now has exactly three targets: local `dustui_go`, a separate
M03-ready hosted `dustui_hosted_go` declaration, and the frozen reviewed
`dustui_hosted_r36` debug-only candidate. Wrapper v2 exposes only read-only
source checks and local Go development; default is `ci-preflight`.

The historical gate targets, local Android Gradle execution graph, AUD-FIX
aliases, and generated migration qualification workflows are no longer Devtool
configuration entries. Archived migration files still exist until M06; the
existing GitHub Actions Android R36 runner is preserved until M04 cutover.
M02 does not qualify hosted Go, Android release, signing or devices.


## M03 native hosted Go provider

GitHub Actions now executes native Devtool Go DAG nodes for test/vet/Linux-x64 CGO
race/build; no independent Go test shell commands in the workflow. Each job has
its own pristine checkout, exact Devtool source pin, deterministic graph plan,
source/host identity checks and typed Devtool evidence. Go CI is **source-only**;
it does not attest Android APK contents, signing identity or a device session.
The R36 Android debug workflow is unchanged pending M04.
