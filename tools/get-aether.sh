#!/usr/bin/env bash
# Damavand: fetch patterniha/Aether release binaries (aether + psiphon-tunnel-core
# + lyrebird) into jniLibs as lib*.so so Android packs them as native libs and
# the app can exec them from nativeLibraryDir. Sha256-verified.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORES_TXT="$ROOT/tools/cores.txt"
CACHE="${CORES_CACHE:-$ROOT/build/cores}"

digest() { sha256sum "$1" | awk '{print $1}'; }

fetch() {
  local asset=$1 dir base want
  dir="$CACHE/aether/$AETHER_TAG"
  base="https://github.com/patterniha/Aether/releases/download/$AETHER_TAG"
  mkdir -p "$dir"
  [ -s "$dir/$asset.sha256" ] || curl -fsSL --retry 3 -o "$dir/$asset.sha256" "$base/$asset.sha256"
  want="$(awk '{print $1}' "$dir/$asset.sha256")"
  if [ ! -s "$dir/$asset" ] || [ "$(digest "$dir/$asset")" != "$want" ]; then
    curl -fsSL --retry 3 -o "$dir/$asset" "$base/$asset"
    [ "$(digest "$dir/$asset")" = "$want" ] || { echo "$asset checksum mismatch" >&2; exit 1; }
  fi
  printf '%s\n' "$dir/$asset"
}

untar_py() {
  python3 - "$1" "$2" "$3" <<'PYEOF'
import sys, tarfile, shutil, os
tar_path, member, out = sys.argv[1], sys.argv[2], sys.argv[3]
with tarfile.open(tar_path) as t:
    m = [x for x in t.getmembers() if x.name == member][0]
    f = t.extractfile(m)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'wb') as g:
        shutil.copyfileobj(f, g)
PYEOF
}

AETHER_TAG="${AETHER_TAG:-v2.4.0}"
jni="$ROOT/app/src/main/jniLibs"
declare -A ABI_TARBALL=( [arm64-v8a]=aether-android-arm64 [armeabi-v7a]=aether-android-armv7 [x86_64]=aether-android-x86_64 )

for abi in arm64-v8a armeabi-v7a x86_64; do
  tarball="$(fetch "${ABI_TARBALL[$abi]}.tar.gz")"
  untar_py "$tarball" "aether" "$jni/$abi/libaether.so"
  untar_py "$tarball" "pt/psiphon-tunnel-core" "$jni/$abi/libpsiphon-tunnel-core.so"
  untar_py "$tarball" "pt/lyrebird" "$jni/$abi/liblyrebird.so"
  chmod +x "$jni/$abi/libaether.so" "$jni/$abi/libpsiphon-tunnel-core.so" "$jni/$abi/liblyrebird.so"
  # size: strip debug info from the core binaries (llvm-strip from the NDK when present)
  STRIP="$(ls -1 "$ANDROID_HOME/ndk"/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip 2>/dev/null | head -1 || true)"
  if [ -n "$STRIP" ]; then
    for f in "$jni/$abi/libaether.so" "$jni/$abi/libpsiphon-tunnel-core.so" "$jni/$abi/liblyrebird.so"; do
      "$STRIP" --strip-debug "$f" || true
    done
  fi
done
echo "aether core staged for 3 ABIs"
