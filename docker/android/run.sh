#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
image_name=shiyin-build:jdk25-sdk37
command -v docker >/dev/null || { printf '%s\n' 'Docker is required.' >&2; exit 1; }
mkdir -p "$project_root/.container"
# A single output/cache directory must not be modified by two container builds.
exec 9>"$project_root/.container/build.lock"
flock -n 9 || { printf '%s\n' 'Another container build is running.' >&2; exit 1; }
if ! docker image inspect "$image_name" >/dev/null 2>&1; then
  docker build --progress=plain --platform linux/amd64 -t "$image_name" "$project_root/docker/android"
fi
docker run --rm --platform linux/amd64 \
  --user "$(id -u):$(id -g)" \
  --mount "type=bind,src=$project_root,dst=/source,readonly" \
  --mount "type=bind,src=$project_root/.container,dst=/build" \
  "$image_name" "$@"
