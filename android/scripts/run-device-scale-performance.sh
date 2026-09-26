#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

adb get-state >/dev/null
entries="${DUSTUI_SCALE_ENTRIES:-100000}"
max_millis="${DUSTUI_SCALE_MAX_MILLIS:-240000}"

python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.dustui.validation.DeviceScalePerformanceInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.dustui.entries="$entries" \
  -Pandroid.testInstrumentationRunnerArguments.dustui.maxMillis="$max_millis"

printf 'DustUI device-scale performance validation passed for %s entries\n' "$entries"
