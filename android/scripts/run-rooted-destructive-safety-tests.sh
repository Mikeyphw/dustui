#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

adb get-state >/dev/null
root_uid="$(adb shell su -c id -u 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
if [[ "$root_uid" != "0" ]]; then
  echo "Rooted destructive-safety validation requires adb device with working su" >&2
  exit 2
fi

python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.dustui.validation.RootedDestructiveSafetyInstrumentedTest

printf 'DustUI rooted destructive-safety validation passed\n'
