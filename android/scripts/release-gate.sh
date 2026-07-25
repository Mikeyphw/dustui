#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
./gradlew --no-daemon --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease
for artifact in app/build/outputs/apk/debug/app-debug.apk; do
  test -s "$artifact" || { echo "missing release-gate artifact: $artifact" >&2; exit 1; }
done
release_count="$(find app/build/outputs/apk/release -type f -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
if [[ "$release_count" -lt 1 ]]; then
  echo "missing release-gate artifact: release APK" >&2
  exit 1
fi
test -d app/schemas || { echo "Room schemas were not exported" >&2; exit 1; }
test -s scripts/device-certify.sh || { echo "device certification script is missing" >&2; exit 1; }
test -s scripts/install-smoke.sh || { echo "install smoke script is missing" >&2; exit 1; }
test -s scripts/final-rc-gate.sh || { echo "final RC gate script is missing" >&2; exit 1; }
bash -n scripts/device-certify.sh
bash -n scripts/install-smoke.sh
bash -n scripts/package-rc.sh
bash -n scripts/final-rc-gate.sh
bash -n scripts/cleaning-intelligence-gate.sh
python3 -m py_compile scripts/cleaning-intelligence-audit.py
DUSTUI_SKIP_BUILD=1 bash scripts/cleaning-intelligence-gate.sh
DUSTUI_SKIP_BUILD=1 DUSTUI_REQUIRE_CLEANING_EVIDENCE=1 bash scripts/package-rc.sh
printf 'DustUI release gate passed
'
