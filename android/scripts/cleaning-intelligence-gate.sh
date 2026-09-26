#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
if [[ "${DUSTUI_SKIP_BUILD:-0}" != "1" ]]; then
  python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 testDebugUnitTest lintDebug
fi
python3 scripts/cleaning-intelligence-audit.py
printf 'DustUI cleaning intelligence gate passed\n'
