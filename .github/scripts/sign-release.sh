#!/usr/bin/env bash
# Sign the embedded guard first. Modifying the asset after signing the app invalidates its signature.
set -euo pipefail
unsigned=$1
key=$2
output=$3
tools=$(printf '%s\n' "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
tools=${tools%/apksigner}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
asset=assets/shiyin-guard.apk

mkdir "$work/assets"
unzip -p "$unsigned" "$asset" > "$work/guard-unsigned.apk"
manifest=$("$tools/aapt" dump badging "$work/guard-unsigned.apk")
grep -q "^package: name='com.Huffer342.shiyin.guard' " <<< "$manifest"
if grep -qx 'application-debuggable' <<< "$manifest"; then
  echo 'Expected a Release guard APK' >&2
  exit 1
fi
sign() {
  "$tools/apksigner" sign --ks "$key" --ks-pass env:STORE_PASSWORD --out "$2" "$1"
}
sign "$work/guard-unsigned.apk" "$work/$asset"
cp "$unsigned" "$work/app.apk"
(cd "$work" && zip -qd app.apk "$asset" && zip -q -0 app.apk "$asset")
"$tools/zipalign" -P 16 -f 4 "$work/app.apk" "$work/aligned.apk"
sign "$work/aligned.apk" "$output"

unzip -p "$output" "$asset" > "$work/embedded.apk"
certificate() {
  "$tools/apksigner" verify --print-certs "$1" | awk '/certificate SHA-256 digest:/ {print $NF}' | sort -u
}
app_cert=$(certificate "$output")
guard_cert=$(certificate "$work/embedded.apk")
[[ -n "$app_cert" && "$app_cert" == "$guard_cert" ]]
