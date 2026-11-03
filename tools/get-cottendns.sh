#!/usr/bin/env bash
# Damavand v2: build the CottenDNS client (WhiteDNS/CottenDNS, MIT) from the
# pinned commit and stage it as jniLibs/arm64-v8a/libcottendns.so so Android
# packs it as a native lib and the app can exec it from nativeLibraryDir.
#
# Recipe verified in the Phase-0 spike (~/workspace/Damavand-rewrite/
# phase0-cottendns.md): the client is a plain Go executable with an Android
# build variant (cmd/client/terminal_ui_android.go); a CGO-free cross-build
# produces a proper Android PIE. No NDK required.
#
# Pin: 77bf87db28af6b64a4b6c404074982235272ed9b (the commit WhiteDNS-Android
# 1.6.15 ships). The built binary's sha256 is appended to tools/cores.txt.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORES_TXT="$ROOT/tools/cores.txt"
CACHE="${CORES_CACHE:-$ROOT/build/cores}"
PIN="77bf87db28af6b64a4b6c404074982235272ed9b"
SRC="$CACHE/cottendns-src"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a/libcottendns.so"

command -v go >/dev/null 2>&1 || { echo "go toolchain not found (need >= 1.26)"; exit 1; }

mkdir -p "$CACHE" "$(dirname "$OUT")"
if [ ! -d "$SRC/.git" ]; then
  git clone https://github.com/WhiteDNS/CottenDNS "$SRC"
fi
git -C "$SRC" fetch --quiet origin "$PIN" || git -C "$SRC" fetch --quiet origin
git -C "$SRC" checkout --quiet "$PIN"
[ "$(git -C "$SRC" rev-parse HEAD)" = "$PIN" ] || { echo "cottendns pin mismatch"; exit 1; }

( cd "$SRC" && GOOS=android GOARCH=arm64 CGO_ENABLED=0 \
    go build -trimpath -ldflags "-s -w" -o "$OUT" ./cmd/client )

digest="$(sha256sum "$OUT" | awk '{print $1}')"
echo "libcottendns.so  commit=$PIN  sha256=$digest"
# Record provenance in cores.txt (idempotent: drop any previous cottendns line).
grep -v '^cottendns' "$CORES_TXT" > "$CORES_TXT.tmp" || true
printf 'cottendns    WhiteDNS/CottenDNS             %s  sha256=%s\n' "$PIN" "$digest" >> "$CORES_TXT.tmp"
mv "$CORES_TXT.tmp" "$CORES_TXT"
