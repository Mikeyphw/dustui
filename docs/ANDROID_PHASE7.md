# Android Phase 7: Release Gate

Phase 7 adds enforceable release checks rather than new cleanup capability.

## Automated gates

- Debug and minified release assembly
- Unit tests and zero-warning lint
- Room schema export
- Tasker allow-list security tests
- Quarantine-manifest validation tests
- Path fingerprint replacement detection
- Native scanner stress validation

Run `android/scripts/release-gate.sh` for the complete local gate.

## Device-only gates

Before publishing, record successful smoke runs on at least one rooted arm64 device for root grant/denial, `/data` scanning, cancellation, process restart, quarantine, restore, scheduled scans, and APK installation. These checks cannot be truthfully certified by host-side unit tests.
