#!/usr/bin/env bash
set -euo pipefail

test -f /source/gradlew || { printf '%s\n' 'Mount the project read-only at /source.' >&2; exit 1; }
mkdir -p "$HOME" "$GRADLE_USER_HOME" /build/workspace
# Sync only this disposable copy; never alter the source mount or host SDK settings.
rsync -a --delete --no-owner --no-group \
  --exclude='/.container/' --exclude='/.git/' --exclude='/.idea/' \
  --exclude='/.agents/' --exclude='/.codex/' --exclude='/.aws/' \
  --exclude='local.properties' --exclude='.gradle/' --exclude='build/' \
  /source/ /build/workspace/
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > /build/workspace/local.properties
cd /build/workspace
exec bash ./gradlew --no-daemon --build-cache "$@"
