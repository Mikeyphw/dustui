# DustUI OV01 legacy reachability map

## Still reachable after OV01

- Python/Textual `src/dust_tui` remains a compatibility/parity oracle until the Go CLI/TUI cutover.
- C++ scanner remains the production scanner until the Go scanner and ScanAuthority gates land.
- Kotlin Room/domain repositories remain production state for current screens until staged Go state/read cutovers.
- Legacy cleanup review/execution remains reachable, but source scans must be complete/current and reviewed plan publication must fail closed.
- Raw ADB shell scripts remain as compatibility helpers only; Devtool is the intended generic build/device/install/release evidence authority.
- Existing 16-destination Android shell remains the product UI until the staged shell remodel.

## Newly unreachable or blocked by OV01

- Unbounded privileged root-service task admission through `Executors.newCachedThreadPool()`.
- Direct overwrite fallback when reviewed cleanup plan atomic publication fails.
- Cleanup review from truncated, non-complete, stale, empty, or scan-mismatched source entries.
- Release-plan paths that resolve to Debug Gradle tasks.
- Scheduled-scan result payloads that imply terminal scan success merely because launch/admission happened.

## Not retired yet

No Python/C++/Room/UI production subsystem is physically removed in OV01. Deletion-only closure is forbidden; later overlays must add behavioral replacement evidence before retiring legacy source/token guards.
