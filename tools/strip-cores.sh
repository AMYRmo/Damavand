#!/usr/bin/env bash
# Damavand: shrink the APK by stripping debug info from the Go/Rust core
# libraries inside zedcore.aar (sing-box libgojni.so dominates the size).
# The AAR is unpacked, every .so is stripped with the NDK's llvm-strip and
# repacked. Skipping gracefully when no NDK is available.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAR="$ROOT/app/libs/zedcore.aar"

[ -f "$AAR" ] || { echo "zedcore.aar missing; skip strip" >&2; exit 0; }

ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
STRIP="$(ls -1 "$ANDROID_HOME/ndk"/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip 2>/dev/null | head -1 || true)"
if [ -z "$STRIP" ]; then
  echo "llvm-strip not found; skip strip" >&2
  exit 0
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

python3 - "$AAR" "$WORK" <<'PYEOF'
import sys, zipfile
aar, work = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(aar) as z:
    z.extractall(work)
PYEOF

for so in "$WORK"/jni/*/*.so; do
  [ -f "$so" ] || continue
  before=$(stat -c%s "$so")
  "$STRIP" --strip-debug "$so" || true
  after=$(stat -c%s "$so")
  echo "stripped $(basename "$so"): $((before/1048576))MB -> $((after/1048576))MB"
done

python3 - "$AAR" "$WORK" <<'PYEOF'
import sys, zipfile, os
aar, work = sys.argv[1], sys.argv[2]
out = aar + ".tmp"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for base, _, files in os.walk(work):
        for f in files:
            full = os.path.join(base, f)
            z.write(full, os.path.relpath(full, work))
os.replace(out, aar)
PYEOF

echo "zedcore.aar stripped"
