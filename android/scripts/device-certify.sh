#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
adb_bin="${ADB:-adb}"
package="${DUSTUI_PACKAGE:-com.mikeyphw.dustui.debug}"
out="${1:-build/device-certification}"
mkdir -p "$out"
version_file="$out/versions.txt"
printf 'package=%s\n' "$package" > "$version_file"
printf 'adb=%s\n' "$adb_bin" >> "$version_file"
grep -E 'version(Name|Code) =' app/build.gradle.kts >> "$version_file" || true
"$adb_bin" get-state > "$out/adb-state.txt"
"$adb_bin" shell getprop > "$out/getprop.txt"
"$adb_bin" shell dumpsys package "$package" > "$out/package.txt"
"$adb_bin" shell dumpsys activity services "$package" > "$out/services.txt"
"$adb_bin" shell dumpsys notification --noredact > "$out/notifications.txt"
"$adb_bin" shell dumpsys jobscheduler > "$out/jobscheduler.txt"
"$adb_bin" shell dumpsys deviceidle > "$out/deviceidle.txt" || true
"$adb_bin" shell dumpsys battery > "$out/battery.txt" || true
"$adb_bin" shell dumpsys window > "$out/window.txt" || true
"$adb_bin" shell pm path "$package" > "$out/apk-path.txt"
"$adb_bin" shell appops get "$package" > "$out/appops.txt" || true
"$adb_bin" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 > "$out/launch.txt"
"$adb_bin" logcat -d -v threadtime DustUI:D AndroidRuntime:E '*:S' > "$out/logcat.txt" || true
tar -czf "$out.tar.gz" -C "$(dirname "$out")" "$(basename "$out")"
printf 'Device certification evidence written to %s and %s.tar.gz\n' "$out" "$out"
