#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.dustui.validation.DustDatabaseMigrationInstrumentedTest

printf 'DustUI Room migration validation passed\n'
