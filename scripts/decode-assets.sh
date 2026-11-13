#!/usr/bin/env bash
# Decodes base64-encoded binary assets (stored as *.b64 under assets-b64/
# because the upload channel only supports UTF-8 text). Run from the
# repository root. Skips any file whose decoded target already exists
# (e.g. if the raw file was uploaded later through the GitHub web UI).
set -euo pipefail
cd "$(dirname "$0")/.."

find assets-b64 -type f -name '*.b64' | sort | while read -r src; do
  dest="app/src/main/${src#assets-b64/}"
  dest="${dest%.b64}"
  if [ -f "$dest" ]; then
    echo "exists, skipping: $dest"
    continue
  fi
  mkdir -p "$(dirname "$dest")"
  base64 -d "$src" > "$dest"
  echo "decoded: $src -> $dest"
done
