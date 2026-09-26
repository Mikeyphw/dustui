#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

adb get-state >/dev/null
root_uid="$(adb shell su -c id -u 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
if [[ "$root_uid" != "0" ]]; then
  echo "Multi-user residue validation requires adb device with working su" >&2
  exit 2
fi

users="$(adb shell pm list users | tr -d '\r' || true)"
user_count="$(printf '%s\n' "$users" | grep -c 'UserInfo{' || true)"
if [[ "${DUSTUI_REQUIRE_MULTI_USER:-1}" == "1" && "$user_count" -lt 2 ]]; then
  echo "Validation device must have at least one secondary user or work profile." >&2
  echo "Current users:" >&2
  printf '%s\n' "$users" >&2
  exit 3
fi

python3 "$(git rev-parse --show-toplevel)/scripts/devtool_gradle_bridge.py" --no-daemon --max-workers=1 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.dustui.validation.MultiUserResidueValidationInstrumentedTest \
  -Pandroid.testInstrumentationRunnerArguments.dustui.requireMultiUser="${DUSTUI_REQUIRE_MULTI_USER:-true}" \
  ${DUSTUI_PACKAGE_UNDER_TEST:+-Pandroid.testInstrumentationRunnerArguments.dustui.packageUnderTest=$DUSTUI_PACKAGE_UNDER_TEST}

printf 'DustUI multi-user residue validation passed\n'
