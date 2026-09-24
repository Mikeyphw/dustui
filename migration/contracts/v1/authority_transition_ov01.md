# DustUI OV01 authority transition

OV01 is a measurement and containment overlay. It does **not** move production scan, state, cleanup, package, diagnostics, automation, UI, or release authority to the new Go engine.

## Authority after OV01

| Domain | Authority after OV01 | Notes |
| --- | --- | --- |
| Scan execution | Legacy Kotlin foreground service + C++ scanner | Go target exists only as readiness/test identity. |
| State | Legacy Room v3 | Go SQLite is not introduced until OV02. |
| Cleanup review/execution | Legacy Kotlin/root-service path, constrained | Cleanup rejects incomplete/degraded/stale/incompatible source scans before root review. |
| Privileged admission | Legacy root service, bounded | Cached unbounded executor is replaced by bounded ThreadPoolExecutor + finite queue. |
| Reviewed plan publication | Legacy cache-file handoff, fail-closed | Direct overwrite fallback is removed; rename failure blocks execution. |
| Automation | Legacy WorkManager/Tasker | Scheduled launch returns `LAUNCHED_NOT_TERMINAL`, not terminal scan success. |
| Release/build/device/install evidence | Devtool | Release plan uses Release tasks; raw scripts remain compatibility helpers until Devtool release qualification replaces them. |
| Go engine | Non-authoritative readiness skeleton | Target exists so EXO can model it from OV01 onward. |

## Stop rule

DustUI OV01 can be prepared while the Devtool EXO-P0 prerequisite is still being fixed, but no milestone gate may treat EXO evidence as authoritative unless the installed `devtool` entrypoint proves terminal run finalization, graph sealing, history snapshot publication, and replay without identity reuse.
