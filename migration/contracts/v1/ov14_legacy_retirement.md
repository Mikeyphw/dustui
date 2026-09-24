# DustUI OV14 Legacy Retirement Contract — truth-corrected

OV14 is the post-Gate-D structural purge. The original OV14 ledger recorded the
retirement target as if it had already been achieved. A source audit found that
this was not true. This document now distinguishes **desired retirement state**
from **observed production wiring**.

Desired production status (the condition required before Gate D may close):

```text
cpp_scanner_runtime = none
python_runtime = none
room_domain_authority = none
legacy_destructive_executor = none
legacy_navigation = none
go_authority = product-default-qualified
android_go_runtime_binding = production
go_sqlite_persistence = go-owned-sqlite
generic_release_truth = devtool-only
```

Observed state at the OV14 truth hotfix:

```text
cpp_scanner_runtime = active-production
python_runtime = compatibility-only-target-present
room_domain_authority = active-production
legacy_destructive_executor = active-production
legacy_navigation = not-proven-retired
go_authority = go-domain-contracts-present-android-production-unqualified
android_go_runtime_binding = missing-production-binding
go_sqlite_persistence = missing-implementation
generic_release_truth = duplicated-app-local
```

## Evidence that reopens Gate D

- `NativeDustScanner` is loaded from `dustui_scanner` and is called by
  `DustRootService`; Android Gradle still builds the CMake target.
- `AppContainer` still constructs Room/DustDatabase/ScanRepository production
  state.
- the Android Go package contains session/product contracts but no child-process
  binding that launches the fixed Go runtime path, and the Android build has no
  Go runtime packaging task for `libdustui_go.so`.
- the Go state package names `go_sqlite`, but the Go module has no SQLite driver
  and no `database/sql` persistence implementation.
- the Python compatibility Devtool target still exists.
- the Compose shell still uses the legacy navigation model; retirement is not
  proven merely by declaring a six-route target model elsewhere.
- app-local generic release/install/certification scripts and release Kotlin
  authority still coexist with the Devtool release-authority policy.

## Enforcement

`python3 scripts/validate_ov14_retirement_truth_hotfix.py` is an audit: it passes
when the ledger accurately matches source, even while blockers exist.

`python3 scripts/validate_ov14_retirement_truth_hotfix.py --gate` is the Gate-D
retirement condition: it fails while any blocker remains. The Devtool `gate_d`
and `validate` target-local workflows run this gate first. `./devtoolw
cutover-audit` provides the non-sealing diagnostic path.

OV15 Gate E must not be created or treated as a final release seal until this
retirement gate passes against the real source tree.
