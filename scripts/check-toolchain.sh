#!/usr/bin/env bash
# Verifies that Maven and a JDK 21 toolchain are reachable before anything else is attempted.
set -euo pipefail

cd "$(dirname "$0")/.."

fail=0

if ! command -v java >/dev/null 2>&1; then
  echo "java not found on PATH. Install a JDK 21 (Temurin recommended)." >&2
  fail=1
else
  version=$(java -version 2>&1 | head -n 1)
  echo "java:  $version"
  major=$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')
  if [ "$major" -lt 21 ]; then
    echo "  LIFEOS targets Java 21; found $major." >&2
    fail=1
  fi
fi

if command -v mvn >/dev/null 2>&1; then
  echo "maven: $(mvn -v 2>&1 | head -n 1)"
else
  echo "maven not found on PATH. Install Maven 3.9+ or use ./mvnw." >&2
  fail=1
fi

if command -v node >/dev/null 2>&1; then
  echo "node:  $(node --version)"
  node_major=$(node --version | sed -E 's/^v([0-9]+).*/\1/')
  if [ "$node_major" -lt 20 ]; then
    echo "  The frontend needs Node 20.19+ or newer." >&2
    fail=1
  fi
else
  echo "node not found on PATH. Required for the frontend only." >&2
fi

if command -v npm >/dev/null 2>&1; then
  echo "npm:   $(npm --version)"
else
  echo "npm not found on PATH. Required for the frontend only." >&2
fi

if command -v docker >/dev/null 2>&1; then
  echo "docker: $(docker --version)"
else
  echo "docker not found on PATH. Required only for the compose stack." >&2
fi

exit "$fail"
