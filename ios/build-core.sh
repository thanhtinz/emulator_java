#!/usr/bin/env bash
# Translates the shared emulator core to Objective-C for the iOS build.
#
#   brew install j2objc      # or download from github.com/google/j2objc
#   ./build-core.sh
#
# Output lands in ios/Generated/, which the Xcode target compiles alongside the
# hand-written bridge. The core is deliberately free of dependencies and of any
# JDK API outside java.lang/util/io/zip, which is exactly the subset J2ObjC's
# runtime provides.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/ios/Generated"
J2OBJC="${J2OBJC_HOME:-$(dirname "$(command -v j2objc)")}"

if ! command -v j2objc >/dev/null 2>&1; then
  echo "j2objc not found. Install it and re-run:" >&2
  echo "  brew install j2objc" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT"

# --nullability and --no-package-directories keep the generated headers easy to
# import from a single include path.
find "$ROOT/core/src" -name '*.java' > "$OUT/sources.txt"
j2objc \
  -d "$OUT" \
  -sourcepath "$ROOT/core/src" \
  -encoding UTF-8 \
  --swift-friendly \
  --nullability \
  -use-arc \
  @"$OUT/sources.txt"

echo "Translated $(wc -l < "$OUT/sources.txt") files into $OUT"
echo "J2ObjC runtime headers and libraries live under: $J2OBJC/../include and /lib"
