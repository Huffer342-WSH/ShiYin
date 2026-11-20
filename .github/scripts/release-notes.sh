#!/usr/bin/env bash
set -euo pipefail

tag=$1
ref="refs/tags/$tag"
patterns=(--match 'release-v[0-9]*')
if [[ $tag == pre-v* ]]; then
  patterns+=(--match 'pre-v[0-9]*')
fi

range=$ref
if git rev-parse --verify --quiet "$ref^" >/dev/null; then
  previous=$(git describe --first-parent --tags --abbrev=0 "${patterns[@]}" "$ref^" 2>/dev/null) || previous=''
  if [[ -n $previous ]]; then
    range="refs/tags/$previous..$ref"
  fi
fi

printf '## 提交记录\n\n'
git log --reverse --format='- %s (%h)' "$range"
