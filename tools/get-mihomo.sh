#!/usr/bin/env bash
# Damavand: fetch MetaCubeX/mihomo release binaries into jniLibs as libmihomo.so
# so the app can exec them from nativeLibraryDir. Sha256-verified (published .sha256).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${CORES_CACHE:-$ROOT/build/cores}"
jni="$ROOT/app/src/main/jniLibs"

digest() { sha256sum "$1" | awk '{print $1}'; }

fetch() {
  local asset=$1 dir base want
  dir="$CACHE/mihomo/$MIHOMO_TAG"
  base="https://github.com/MetaCubeX/mihomo/releases/download/$MIHOMO_TAG"
  mkdir -p "$dir"
  [ -s "$dir/$asset.sha256" ] || curl -fsSL --retry 3 -o "$dir/$asset.sha256" "$base/$asset.sha256"
  want="$(awk '{print $1}' "$dir/$asset.sha256")"
  if [ ! -s "$dir/$asset" ] || [ "$(digest "$dir/$asset")" != "$want" ]; then
    curl -fsSL --retry 3 -o "$dir/$asset" "$base/$asset"
    [ "$(digest "$dir/$asset")" = "$want" ] || { echo "$asset checksum mismatch" >&2; exit 1; }
  fi
  printf '%s\n' "$dir/$asset"
}

ungz_py() {
  python3 - "$1" "$2" <<'PYEOF'
import sys, gzip, os
src, out = sys.argv[1], sys.argv[2]
os.makedirs(os.path.dirname(out), exist_ok=True)
with gzip.open(src, 'rb') as f, open(out, 'wb') as g:
    g.write(f.read())
PYEOF
}

MIHOMO_TAG="${MIHOMO_TAG:-v1.19.33}"
declare -A ABI_ASSET=( [arm64-v8a]=mihomo-android-arm64-v8 [armeabi-v7a]=mihomo-android-armv7 [x86_64]=mihomo-android-amd64 )

for abi in arm64-v8a armeabi-v7a x86_64; do
  gz="$(fetch "${ABI_ASSET[$abi]}-$MIHOMO_TAG.gz")"
  ungz_py "$gz" "$jni/$abi/libmihomo.so"
  chmod +x "$jni/$abi/libmihomo.so"
  STRIP="$(ls -1 "$ANDROID_HOME/ndk"/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip 2>/dev/null | head -1 || true)"
  if [ -n "$STRIP" ]; then
    "$STRIP" --strip-debug "$jni/$abi/libmihomo.so" || true
  fi
done
echo "mihomo core staged for 3 ABIs"
