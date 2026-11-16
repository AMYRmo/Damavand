#!/usr/bin/env bash
# Build the Damavand Lite core: a minimal Xray-only libv2ray.aar from
# xtls/xray-core + the thin wrapper in core/ (package libv2ray), and
# install it as app/libs/zedcore.aar — the file dependency the app
# module expects, so no Gradle change is needed.
#
# The old zedcore.aar was a mega-core (Xray + sing-box + Psiphon +
# Tailscale + OpenConnect + OpenVPN + gVisor, 108 MB .so) while Lite
# only ever runs Xray. A plain Xray core is ~33 MB (the size v2rayNG
# ships), which is what takes the installed app from ~184 MB to
# under 120 MB.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CORE="$ROOT/core"
OUT="$ROOT/app/libs/zedcore.aar"
mkdir -p "$ROOT/app/libs"

# NDK for gomobile (same lookup the Ddfathu libv2ray builder uses).
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
NDK="$(ls -d "$SDK"/ndk/* 2>/dev/null | tail -n 1 || true)"
if [ -n "$NDK" ]; then
  export ANDROID_NDK_HOME="$NDK"
  export ANDROID_HOME="$SDK"
fi

go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
export PATH="$PATH:$(go env GOPATH)/bin"
gomobile init

cd "$CORE"
go mod tidy
# -s -w: strip the Go symbol table + DWARF (the same diet v2rayNG's
# core ships with). Three ABIs: the APK splits use arm64 + armv7, and
# the universal APK also carries x86_64.
# -androidapi 21: gomobile's default for 32-bit arm is API 16, but the
# runner's NDK (30.x) only supports 21..37. The app's minSdk is 26,
# so 21 is safely inside both ranges.
gomobile bind \
  -target=android/arm64,android/arm,android/amd64 \
  -androidapi 21 \
  -ldflags="-s -w" \
  -o "$OUT" \
  litecore

ls -l "$OUT"
python3 - "$OUT" <<'PYEOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    for i in z.infolist():
        if i.filename.endswith(".so"):
            print(f"{i.filename}: {i.file_size / 1048576:.1f} MB")
PYEOF
