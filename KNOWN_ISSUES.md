
## AUD-FIX-10 validation note
- AUD-FIX-10 is a non-gate UX-truth convergence stage. Gate D remains open; final release qualification still depends on the later remediation gates and evidence registry.
- The stage-owned workflow includes Android unit and Go truth/gate tests; full lint/assemble/validation stays reserved for the next designated roadmap gate.

# DustUI known issues

## Release-blocking issues

None known at the time of this RC ledger.

## Non-blocking notes

- A release APK may be unsigned in local RC evidence until distribution signing is configured.
- Device certification must be collected per target device because root backend, SELinux, battery policy, and notification readiness are device-specific.
- Install smoke evidence is expected to be generated from a real device or emulator with ADB access.

## Review rule

Any item written as `- [blocker] ...`, `- [p0] ...`, or `- [p1] ...` blocks `scripts/final-rc-gate.sh`.

## AUD-FIX-09 validation note
- Full Gate D remains open; AUD-FIX-09 only converges async presentation and action-gate authority. Final release qualification still depends on the later roadmap gates and evidence registry.

- AUD-FIX-11 intentionally does not implement adaptive tablet/master-detail behavior; that remains AUD-FIX-12.

- AUD-FIX-12 implements and statically/unit-tests adaptive route continuity; connected-device rotation/accessibility evidence is defined by `./devtoolw aud-fix-12-device` and remains qualification evidence to execute on a qualifying device/tree. Gate D remains open.
- AUD-FIX-13 converges candidate acceptance only. Gate D remains open; historical Gate A/B/C evidence and complete Gate-D topology remain AUD-FIX-14, while quarantined generic helpers and Room/NDJSON retirement remain later roadmap work.
