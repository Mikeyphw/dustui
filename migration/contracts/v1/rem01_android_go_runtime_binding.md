# REM-01 — Android Go runtime production binding

REM-01 closes only the `go_android_runtime_binding_missing` blocker reopened by the OV14 truth audit.

The Android APK builds an arm64 PIE Go executable at `lib/arm64-v8a/libdustui_go.so`. Android resolves it exclusively from `ApplicationInfo.nativeLibraryDir`, launches it as a child process with `serve`, performs a readiness handshake before accepting requests, bounds every NDJSON frame to 1 MiB, and rejects mismatched request/sequence/generation responses. The child process protocol is `dustui.android.runtime.session.v1`.

This remediation does **not** claim Go SQLite cutover, Room retirement, C++ scanner retirement, destructive executor retirement, Python compatibility retirement, navigation retirement, or release-truth centralization. Gate D therefore remains open after REM-01.
