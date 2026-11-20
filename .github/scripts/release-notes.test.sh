#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/release-notes.sh"
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
git init -q "$repo"
cd "$repo"
git config user.name Test
git config user.email test@example.invalid

check() {
  local tag=$1 expected=$2 notes subjects
  notes=$(bash "$script" "$tag")
  subjects=$(sed -nE 's/^- (.*) \([0-9a-f]+\)$/\1/p' <<< "$notes")
  if [[ $subjects != "$expected" ]]; then
    printf '%s: expected [%s], got [%s]\n' "$tag" "$expected" "$subjects" >&2
    exit 1
  fi
}

git commit -qm first --allow-empty
git tag pre-v0.1.0
check pre-v0.1.0 first
git commit -qm second --allow-empty
git tag -a pre-v0.2.0 -m preview
check pre-v0.2.0 second
git tag release-v0.2.0
check release-v0.2.0 $'first\nsecond'
git commit -qm third --allow-empty
git tag release-v0.3.0
check release-v0.3.0 third
