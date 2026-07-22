#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
adb_bin="${ADB:-adb}"
package="${DUSTUI_PACKAGE:-com.mikeyphw.dustui.debug}"
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
out="${2:-build/install-smoke}"
mkdir -p "$out"

if [[ ! -s "$apk" ]]; then
  echo "APK not found or empty: $apk" >&2
  exit 1
fi

"$adb_bin" get-state > "$out/adb-state.txt"
"$adb_bin" install -r "$apk" > "$out/install.txt"
"$adb_bin" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 > "$out/launch.txt"
sleep "${DUSTUI_SMOKE_WAIT_SECONDS:-3}"
"$adb_bin" shell dumpsys package "$package" > "$out/package.txt"
"$adb_bin" shell dumpsys activity activities > "$out/activities.txt" || true
"$adb_bin" shell pidof "$package" > "$out/pid.txt" || true
"$adb_bin" logcat -d -v threadtime DustUI:D AndroidRuntime:E '*:S' > "$out/logcat.txt" || true
if grep -q 'FATAL EXCEPTION' "$out/logcat.txt"; then
  echo "Fatal exception found in focused logcat" >&2
  exit 1
fi
printf 'DustUI install smoke evidence written to %s\n' "$out"
