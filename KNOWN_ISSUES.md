# DustUI known issues

## Release-blocking issues

None known at the time of this RC ledger.

## Non-blocking notes

- A release APK may be unsigned in local RC evidence until distribution signing is configured.
- Device certification must be collected per target device because root backend, SELinux, battery policy, and notification readiness are device-specific.
- Install smoke evidence is expected to be generated from a real device or emulator with ADB access.

## Review rule

Any item written as `- [blocker] ...`, `- [p0] ...`, or `- [p1] ...` blocks `scripts/final-rc-gate.sh`.
