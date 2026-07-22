#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

version_name="$({ grep -E 'versionName = ' app/build.gradle.kts || true; } | sed -E 's/.*versionName = "([^"]+)".*/\1/' | head -n 1)"
if [[ -z "$version_name" ]]; then
  echo "Unable to read versionName from app/build.gradle.kts" >&2
  exit 1
fi

package_name="${DUSTUI_RELEASE_PACKAGE:-com.mikeyphw.dustui}"
dist_root="${DUSTUI_DIST_DIR:-build/release-candidate}"
out="$dist_root/dustui-$version_name"
mkdir -p "$out"

tasks=(testDebugUnitTest lintDebug assembleDebug assembleRelease)
if [[ "${DUSTUI_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew --no-daemon --max-workers=1 "${tasks[@]}"
fi

mapfile -t apks < <(find app/build/outputs/apk -type f -name '*.apk' | sort)
if [[ "${#apks[@]}" -eq 0 ]]; then
  echo "No APK artifacts found under app/build/outputs/apk" >&2
  exit 1
fi

debug_found=0
release_found=0
signed_release_found=0
manifest="$out/manifest.json"
checksums="$out/SHA256SUMS"
: > "$checksums"

printf '{\n' > "$manifest"
printf '  "app": "DustUI",\n' >> "$manifest"
printf '  "packageName": "%s",\n' "$package_name" >> "$manifest"
printf '  "versionName": "%s",\n' "$version_name" >> "$manifest"
printf '  "artifacts": [\n' >> "$manifest"

first=1
for apk in "${apks[@]}"; do
  base="$(basename "$apk")"
  kind="debug"
  suffix="debug"
  installable=true
  case "$apk" in
    *release*unsigned*.apk)
      kind="release_unsigned"
      suffix="release-unsigned"
      installable=false
      release_found=1
      ;;
    *release*.apk)
      kind="release_signed"
      suffix="release-signed"
      installable=true
      release_found=1
      signed_release_found=1
      ;;
    *debug*.apk)
      kind="debug"
      suffix="debug"
      installable=true
      debug_found=1
      ;;
  esac
  dest="dustui-$version_name-$suffix.apk"
  cp "$apk" "$out/$dest"
  size="$(wc -c < "$apk" | tr -d ' ')"
  sha="$(sha256sum "$apk" | awk '{print $1}')"
  printf '%s  %s\n' "$sha" "$dest" >> "$checksums"
  if [[ "$first" -eq 0 ]]; then
    printf ',\n' >> "$manifest"
  fi
  first=0
  printf '    {"source":"%s","file":"%s","kind":"%s","sizeBytes":%s,"sha256":"%s","installable":%s}' \
    "$base" "$dest" "$kind" "$size" "$sha" "$installable" >> "$manifest"
done

printf '\n  ],\n' >> "$manifest"
printf '  "debugApkPresent": %s,\n' "$([[ "$debug_found" -eq 1 ]] && echo true || echo false)" >> "$manifest"
printf '  "releaseApkPresent": %s,\n' "$([[ "$release_found" -eq 1 ]] && echo true || echo false)" >> "$manifest"
printf '  "signedReleasePresent": %s\n' "$([[ "$signed_release_found" -eq 1 ]] && echo true || echo false)" >> "$manifest"
printf '}\n' >> "$manifest"

if [[ "$debug_found" -ne 1 ]]; then
  echo "Missing debug APK evidence" >&2
  exit 1
fi
if [[ "$release_found" -ne 1 ]]; then
  echo "Missing release APK evidence" >&2
  exit 1
fi
if [[ "$signed_release_found" -ne 1 ]]; then
  echo "Release APK is unsigned; RC evidence was packaged, but distribution signing is still required." >&2
fi

cat > "$out/README.txt" <<README
DustUI $version_name release-candidate bundle

Contents:
- APK artifacts copied from Gradle outputs
- SHA256SUMS for every copied APK
- manifest.json with installability and signing state

Next:
1. Install the preferred APK on a rooted Android device.
2. Run scripts/device-certify.sh against that installed package.
3. Attach the generated device-certification directory to final RC evidence.
README

printf 'DustUI RC bundle written to %s\n' "$out"
