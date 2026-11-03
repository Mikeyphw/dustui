# DustUI M05: release AndroidTest R8 app ABI bridge

## Confirmed device failure (run 41659799055)

AndroidJUnitRunner now starts and discovers all four allowlisted tests, but
all four fail before exercising their intended assertions. The signed app
and signed AndroidTest APK are separately optimized by R8.

- `GoRuntimeProcess.Companion` missing from the release APK class.
- `GoRuntimePathContract` removed/renamed.
- `DustRouteSelection` Kotlin default-argument constructor unavailable.
- A Kotlin `object.INSTANCE` singleton field unavailable to the UI test.

This patch keeps only the M05 test-accessed Go and adaptive UI class families
(and all their members) in **both** R8 inputs. M05-only release keep rules
are enabled by `DUSTUI_M05_INSTRUMENTATION_BUILD=true`; normal release builds
retain their existing configuration and remain minified.

The hosted signed DEX verification now refuses to publish a pair unless the
release APK defines nine exact test-accessed classes. The actual member/
constructor linkage is verified only by rerunning all four device tests;
DEX class-definition checks alone do **not** establish ABI compatibility.

Do not clear app data or perform privileged operations. Generate a new signed
artifact on GitHub Actions; compare its SHA/receipt and run the existing
`plan` / explicit device `run` workflow. `release_qualified=false` remains.

## Run 41673253731: JSON ABI and stderr-reader close race

The signed M05 device test now starts and exercises the Go/SQLite path, but
calls to `JSONObject.optString(String)` from the independently R8-optimized
test APK fail with `NoSuchMethodError` on an obfuscated app-defined class.
M05-only release and AndroidTest keep rules now retain `org.json.**` classes
and their members; the signed DEX class gate checks for `org.json.JSONObject`.
The signed DEX gate additionally requires a public encoded virtual
`JSONObject.optString(String):String` method definition, not merely a
reference or class definition. Full runtime linkage still requires Android instrumentation.

`GoRuntimeProcess` also has a daemon thread reading child-process stderr.
Closing its `BufferedReader` while the thread is blocked throws an Android
`InterruptedIOException`; leaving this uncaught can crash the test runner.
The reader now treats I/O errors during intentional shutdown as expected,
records unexpected read errors as bounded diagnostics, and always signals the
existing drain latch. This shutdown correction affects Go-process lifecycle
across variants, but does not change subprocess startup or privileged access.

No ADB/Gradle is executed locally; the hosted GitHub build and signed device
tests remain required. Preserve `release_qualified=false` until other gates.
