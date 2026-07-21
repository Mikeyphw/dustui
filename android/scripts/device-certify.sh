#!/usr/bin/env bash
set -euo pipefail
adb_bin="${ADB:-adb}"
package="${DUSTUI_PACKAGE:-com.mikeyphw.dustui.debug}"
out="${1:-build/device-certification}"
mkdir -p "$out"
"$adb_bin" get-state > "$out/adb-state.txt"
"$adb_bin" shell getprop > "$out/getprop.txt"
"$adb_bin" shell dumpsys package "$package" > "$out/package.txt"
"$adb_bin" shell dumpsys activity services "$package" > "$out/services.txt"
"$adb_bin" shell dumpsys notification --noredact > "$out/notifications.txt"
"$adb_bin" shell dumpsys jobscheduler > "$out/jobscheduler.txt"
"$adb_bin" shell pm path "$package" > "$out/apk-path.txt"
"$adb_bin" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 > "$out/launch.txt"
"$adb_bin" logcat -d -v threadtime DustUI:D AndroidRuntime:E '*:S' > "$out/logcat.txt" || true
printf 'Device certification evidence written to %s\n' "$out"
