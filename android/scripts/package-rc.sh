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

tasks=(testReleaseUnitTest lintRelease assembleRelease)
if [[ "${DUSTUI_SKIP_BUILD:-0}" != "1" ]]; then
  python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 "${tasks[@]}"
fi

if [[ "${DUSTUI_INCLUDE_DEBUG_APK:-0}" == "1" ]]; then
  mapfile -t apks < <(find app/build/outputs/apk -type f -name '*.apk' | sort)
else
  mapfile -t apks < <(find app/build/outputs/apk/release -type f -name '*.apk' | sort)
fi
if [[ "${#apks[@]}" -eq 0 ]]; then
  echo "No APK artifacts found under app/build/outputs/apk" >&2
  exit 1
fi

cleaning_evidence="build/cleaning-intelligence/qualification.json"
cleaning_summary="build/cleaning-intelligence/QUALIFICATION.md"
cleaning_ready=false
if [[ -s "$cleaning_evidence" ]] && grep -Eq '"ready"[[:space:]]*:[[:space:]]*true' "$cleaning_evidence"; then cleaning_ready=true; fi
if [[ "${DUSTUI_REQUIRE_CLEANING_EVIDENCE:-0}" == "1" && "$cleaning_ready" != "true" ]]; then
  echo "Cleaning intelligence qualification evidence is missing or failed" >&2; exit 1
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
printf '  "signedReleasePresent": %s,\n' "$([[ "$signed_release_found" -eq 1 ]] && echo true || echo false)" >> "$manifest"
printf '  "cleaningIntelligenceGatePassed": %s\n' "$cleaning_ready" >> "$manifest"
printf '}\n' >> "$manifest"

if [[ "$release_found" -ne 1 ]]; then
  echo "Missing release APK evidence" >&2
  exit 1
fi
if [[ "$signed_release_found" -ne 1 ]]; then
  echo "Release APK is unsigned; RC evidence was packaged, but distribution signing is still required." >&2
fi

if [[ -s "$cleaning_evidence" ]]; then cp "$cleaning_evidence" "$out/cleaning-intelligence-qualification.json"; fi
if [[ -s "$cleaning_summary" ]]; then cp "$cleaning_summary" "$out/CLEANING_INTELLIGENCE_QUALIFICATION.md"; fi

cat > "$out/README.txt" <<README
DustUI $version_name release-candidate bundle

Contents:
- APK artifacts copied from Gradle outputs
- SHA256SUMS for every copied APK
- manifest.json with installability, signing state, and cleaning-intelligence qualification
- cleaning-intelligence qualification evidence when available

Next:
1. Install the preferred APK on a rooted Android device.
2. Run scripts/device-certify.sh against that installed package.
3. Attach the generated device-certification directory to final RC evidence.
README

printf 'DustUI RC bundle written to %s\n' "$out"
