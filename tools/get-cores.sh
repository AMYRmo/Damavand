#!/usr/bin/env bash
# Damavand core fetcher — mirrors ZedSecure's tools/get-cores.sh android path.
# Uses python zipfile instead of unzip (host has no unzip).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${CORES_CACHE:-$ROOT/build/cores}"
CORES_TXT="$ROOT/tools/cores.txt"

digest() { sha256sum "$1" | awk '{print $1}'; }

fetch() {
  local name=$1 asset=$2 repo tag dir base want
  read -r repo tag < <(awk -v n="$name" '$1 == n { print $2, $3 }' "$CORES_TXT")
  [ -n "${tag:-}" ] || { echo "$name is not in cores.txt" >&2; exit 1; }
  dir="$CACHE/$repo/$tag"
  base="https://github.com/$repo/releases/download/$tag"
  mkdir -p "$dir"
  [ -s "$dir/SHA256SUMS" ] || curl -fsSL --retry 3 -o "$dir/SHA256SUMS" "$base/SHA256SUMS"
  want=$(awk -v a="$asset" '$2 == a || $2 == "*" a { print $1 }' "$dir/SHA256SUMS")
  [ -n "$want" ] || { echo "$asset is not in $repo $tag" >&2; exit 1; }
  if [ ! -s "$dir/$asset" ] || [ "$(digest "$dir/$asset")" != "$want" ]; then
    curl -fsSL --retry 3 -o "$dir/$asset" "$base/$asset"
    [ "$(digest "$dir/$asset")" = "$want" ] || { echo "$asset from $repo $tag does not match its checksum" >&2; exit 1; }
  fi
  printf '%s\n' "$dir/$asset"
}

unzip_py() {
  python3 - "$1" "$2" <<'PYEOF'
import sys, zipfile, os
zip_path, dest = sys.argv[1], sys.argv[2]
os.makedirs(dest, exist_ok=True)
with zipfile.ZipFile(zip_path) as z:
    z.extractall(dest)
PYEOF
}

TARGET="${1:-android}"
jni="$ROOT/app/src/main/jniLibs"
mkdir -p "$ROOT/app/libs" "$jni"

cp "$(fetch zedcore zedcore.aar)" "$ROOT/app/libs/zedcore.aar"
for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$jni/$abi"
  for core in tor snispoof hev; do
    unzip_py "$(fetch "$core" "$core-android-$abi.zip")" "$jni/$abi"
  done
done
unzip_py "$(fetch zeptun zeptun-android-jniLibs.zip)" "$CACHE/zeptun-android"
cp -r "$CACHE/zeptun-android/jniLibs/." "$jni/"
find "$jni" -name '*.so' -exec chmod +x {} +
echo "cores for $TARGET are in place"
