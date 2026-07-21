# Android Phase 3: snapshots and app storage

Phase 3 treats completed scans as immutable storage snapshots, compares the latest two completed scans, aggregates paths by Android package, flags package-owned paths whose package is not installed, supports periodic scans through WorkManager, and emits low-space or large-growth notifications after successful scans.

Scheduled scans reuse the most recent validated scan request and create a new scan identifier for every run. Android's 15-minute WorkManager minimum is enforced; the UI currently offers a conservative 24-hour schedule.
