#!/usr/bin/env bash
# Runs the full local quality gate: backend tests plus frontend lint, typecheck and build.
set -euo pipefail

cd "$(dirname "$0")/.."

"$(dirname "$0")/check-toolchain.sh"

echo
echo "==> Backend: mvn verify"
(cd backend && mvn -B verify)

echo
echo "==> Frontend: install"
(cd frontend && npm ci --no-audit --no-fund)

echo
echo "==> Frontend: lint"
(cd frontend && npm run lint)

echo
echo "==> Frontend: typecheck"
(cd frontend && npx tsc -p tsconfig.app.json --noEmit)

echo
echo "==> Frontend: build"
(cd frontend && npm run build)

echo
echo "All checks passed."
