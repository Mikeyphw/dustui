#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug

bash scripts/validate-room-migrations.sh

if [[ "${DUSTUI_SKIP_DEVICE_VALIDATION:-0}" == "1" ]]; then
  echo "Skipping rooted/device validation because DUSTUI_SKIP_DEVICE_VALIDATION=1"
  exit 0
fi

bash scripts/run-rooted-destructive-safety-tests.sh
bash scripts/run-multi-user-residue-validation.sh
bash scripts/run-device-scale-performance.sh

printf 'DustUI full remediation validation passed\n'
