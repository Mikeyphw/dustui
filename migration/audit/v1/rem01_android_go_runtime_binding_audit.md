# REM-01 audit

Source proof required:

- Gradle owns an arm64 Android Go build task and packages its output as `libdustui_go.so` through `jniLibs`.
- `GOOS=android`, `GOARCH=arm64`, `CGO_ENABLED=0`, and `-buildmode=pie` are explicit.
- Android launches only the fixed `nativeLibraryDir/libdustui_go.so` path.
- Go `serve` emits readiness first and then processes bounded NDJSON session frames.
- Android enforces request ID, sequence, and generation correlation.
- OV14 truth audit observes `android_go_runtime_binding=production` while all unrelated blockers remain explicit.
