# DustUI Devtool and release policy after OV01

- `dustui_android` is the default target.
- `dustui_go` is a real, non-authoritative Go target rooted at `go/`.
- `dust_tui_legacy` is a temporary Python/Textual compatibility target.
- Gradle structured events are enabled for Android execution.
- Debug fast workflow remains for non-release development checks.
- Release plan tasks are variant-correct: `testReleaseUnitTest`, `lintRelease`, and `assembleRelease`.
- Generic device/install/artifact/release truth belongs to Devtool, not app-local shell scripts.
- App-local scripts may remain as compatibility or Dust-specific semantic gates until OV14/OV15 retirement.
- Every Gradle task passed manually to Devtool must use a separate `--task` flag.
