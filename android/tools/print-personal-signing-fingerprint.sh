#!/usr/bin/env bash
set -euo pipefail
umask 077
CONFIG_PATH="${DUSTUI_ANDROID_SIGNING_PROPERTIES:-$HOME/.config/devtool/dustui/android-signing.properties}"
fail() { printf 'DustUI fingerprint failed: %s\n' "$*" >&2; exit 1; }
command -v keytool >/dev/null 2>&1 || fail "keytool was not found"
[[ -f "$CONFIG_PATH" ]] || fail "signing properties not found: $CONFIG_PATH"
store_file="$(sed -n 's/^storeFile=//p' "$CONFIG_PATH" | head -1)"
key_alias="$(sed -n 's/^keyAlias=//p' "$CONFIG_PATH" | head -1)"
[[ -n "$store_file" && -f "$store_file" ]] || fail "keystore not found: $store_file"
[[ -n "$key_alias" ]] || fail "keyAlias is missing from $CONFIG_PATH"
IFS= read -r -s -p 'Keystore password: ' password
printf '\n'
export DUSTUI_SIGNING_FINGERPRINT_PASSWORD="$password"
keytool -list -v \
  -keystore "$store_file" \
  -alias "$key_alias" \
  -storepass:env DUSTUI_SIGNING_FINGERPRINT_PASSWORD | grep -E 'SHA1:|SHA256:'
unset DUSTUI_SIGNING_FINGERPRINT_PASSWORD password
