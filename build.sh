#!/usr/bin/env bash
# MobiCore build helper.
#
#   ./build.sh core   compile the portable emulator core
#   ./build.sh test   compile everything and run the test suite
#   ./build.sh run …  run a class from the desktop tools module
#
# The core has no third-party dependencies on purpose: it must compile with a
# bare JDK today and translate cleanly for iOS tomorrow.
set -euo pipefail

export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build/classes"
SRC_LEVEL=8

compile() {
  local out="$1"; shift
  local cp="$1"; shift
  mkdir -p "$out"
  local files
  files=$(find "$@" -name '*.java')
  if [ -z "$files" ]; then return 0; fi
  # shellcheck disable=SC2086
  javac -nowarn -encoding UTF-8 -source "$SRC_LEVEL" -target "$SRC_LEVEL" \
    ${cp:+-cp "$cp"} -d "$out" $files 2>&1 | grep -v 'bootstrap class path\|source value 8\|target value 8\|deprecat' || true
}

build_core() {
  compile "$OUT/core" "" "$ROOT/core/src"
}

build_tools() {
  build_core
  if [ -d "$ROOT/tools/src" ]; then
    compile "$OUT/tools" "$OUT/core" "$ROOT/tools/src"
  fi
}

# Fixtures are ordinary Java programs compiled to bytecode; the test suite
# feeds them to the interpreter, so they must be built for the class file
# version the emulator targets, not for the host JDK.
build_fixtures() {
  if [ -d "$ROOT/fixtures/src" ]; then
    compile "$OUT/fixtures" "" "$ROOT/fixtures/src"
  fi
}

build_tests() {
  build_tools
  build_fixtures
  compile "$OUT/tests" "$OUT/core:$OUT/tools" "$ROOT/tests/src"
}

case "${1:-test}" in
  core) build_core; echo "core compiled -> $OUT/core" ;;
  tools) build_tools; echo "tools compiled -> $OUT/tools" ;;
  fixtures) build_fixtures; echo "fixtures compiled -> $OUT/fixtures" ;;
  test)
    build_tests
    java -cp "$OUT/core:$OUT/tools:$OUT/tests" com.mobicore.tests.Runner "$OUT/fixtures"
    ;;
  run)
    shift
    build_tools
    java -cp "$OUT/core:$OUT/tools" "$@"
    ;;
  clean) rm -rf "$ROOT/build"; echo "cleaned" ;;
  *) echo "usage: $0 {core|tools|fixtures|test|run|clean}" >&2; exit 2 ;;
esac
