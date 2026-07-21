#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
./gradlew --no-daemon --max-workers=1 clean testDebugUnitTest lintDebug assembleDebug assembleRelease
for artifact in app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk; do
  test -s "$artifact" || { echo "missing release-gate artifact: $artifact" >&2; exit 1; }
done
test -d app/schemas || { echo "Room schemas were not exported" >&2; exit 1; }
test -x scripts/device-certify.sh || { echo "device certification script is not executable" >&2; exit 1; }
echo "DustUI release gate passed"
