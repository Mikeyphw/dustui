# OV14 Retirement Truth Re-audit

The OV14 audit loop was rerun against production source instead of accepting
ledger declarations as evidence. The previous `none` claims were not supported
by the repository wiring.

## Findings

1. **C++ scanner remains production-active.** `NativeDustScanner` loads
   `dustui_scanner`; `DustRootService` invokes scan/cancel/delete methods; CMake
   remains wired in the Android app build.
2. **Legacy destructive execution remains reachable.** Root cleanup still calls
   `NativeDustScanner.deleteReviewedPath`.
3. **Room remains production-active.** `AppContainer` constructs Room,
   `DustDatabase`, and `ScanRepository`.
4. **Android Go runtime binding is absent.** Contracts exist, but there is no
   concrete Android process launcher plus build/package path for
   `libdustui_go.so`.
5. **Go-owned SQLite is not implemented.** The module declares schema/read
   authority but has neither a SQLite driver dependency nor `database/sql`
   persistence implementation.
6. **Python compatibility target remains.** It is marked compatibility-only,
   but this is not the final `python_runtime = none` condition.
7. **Legacy navigation retirement is not proven.** The Android shell still
   consumes the old navigation/domain surface.
8. **Generic release truth is duplicated.** App-local release/install/device
   gate scripts and release authority Kotlin remain despite Devtool being the
   declared generic release authority.

## Audit-loop decision

Gate D is reopened. OV15 Gate E is blocked. The repository now has two separate
EXO paths: a passing `cutover_audit` that records truthful blockers and a
`retirement_gate` that intentionally fails until the actual cutover is complete.
This prevents declarations, contract-only scaffolding, or stale migration
ledgers from producing another false seal.
