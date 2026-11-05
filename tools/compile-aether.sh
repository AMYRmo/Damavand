#!/bin/bash
# Damavand: build libaether.so (patterniha/Aether core) as an executable .so
# staged into libs-aether/<abi>/libaether.so. Mirrors PattNG's compile-aether.sh.
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$__dir/aether/aether"
if [[ ! -f "$CORE_DIR/Cargo.toml" ]]; then
  echo "Aether core sources not found at $CORE_DIR" >&2
  exit 1
fi

ABIS="armeabi-v7a arm64-v8a x86_64"
API_LEVEL=29

triple_for () {
  case "$1" in
    armeabi-v7a) echo "armv7-linux-androideabi" ;;
    arm64-v8a)   echo "aarch64-linux-android" ;;
    x86_64)      echo "x86_64-linux-android" ;;
    *) echo "unsupported abi: $1" >&2; exit 1 ;;
  esac
}
clang_for () {
  case "$1" in
    armeabi-v7a) echo "armv7a-linux-androideabi$API_LEVEL" ;;
    *)           echo "$(triple_for "$1")$API_LEVEL" ;;
  esac
}

TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
SYSROOT="$(cd "$TOOLCHAIN/../sysroot" && pwd)"
OUT_DIR="$__dir/libs-aether"
mkdir -p "$OUT_DIR"

for abi in $ABIS; do
  triple="$(triple_for "$abi")"
  clang_target="$(clang_for "$abi")"
  env_triple="$(echo "$triple" | tr 'a-z-' 'A-Z_')"
  under_triple="$(echo "$triple" | tr '-' '_')"

  rustup target add "$triple" >/dev/null 2>&1 || true

  echo "[aether] building the core for $abi ($triple)"
  env \
    ANDROID_NDK_HOME="$NDK_HOME" \
    ANDROID_NDK_ROOT="$NDK_HOME" \
    "CARGO_TARGET_${env_triple}_LINKER=$TOOLCHAIN/$clang_target-clang" \
    "CARGO_TARGET_${env_triple}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384" \
    "CC_${under_triple}=$TOOLCHAIN/clang" \
    "CXX_${under_triple}=$TOOLCHAIN/clang++" \
    "CFLAGS_${under_triple}=--target=$clang_target" \
    "CXXFLAGS_${under_triple}=--target=$clang_target" \
    "AR_${under_triple}=$TOOLCHAIN/llvm-ar" \
    "BINDGEN_EXTRA_CLANG_ARGS_${under_triple}=--target=$clang_target --sysroot=$SYSROOT" \
    "BORING_BSSL_RUST_CPPLIB_${under_triple}=static:-bundle=c++" \
    cargo build --release --locked --features tor --manifest-path "$CORE_DIR/Cargo.toml" --target "$triple" --bin aether

  produced="$CORE_DIR/target/$triple/release/aether"
  [[ -f "$produced" ]] || { echo "aether binary missing for $abi" >&2; exit 1; }

  needed="$("$TOOLCHAIN/llvm-readelf" --dynamic-table "$produced" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p')"
  echo "[aether] the core for $abi needs:" $needed
  if grep -qx 'libc++_shared.so' <<< "$needed"; then
    echo "the core for $abi needs libc++_shared.so, which Android does not ship" >&2
    exit 1
  fi

  mkdir -p "$OUT_DIR/$abi"
  cp "$produced" "$OUT_DIR/$abi/libaether.so"
done

echo "[aether] staged:"
for abi in $ABIS; do ls -la "$OUT_DIR/$abi/libaether.so"; done
