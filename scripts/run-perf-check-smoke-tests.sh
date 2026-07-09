#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-perf-check-smoke-tests.sh
#
# Runs @SmokeTest-annotated tests in infrastructure mode against an already-
# running docker-compose.performance-check.yml stack (Postgres + app).
#
# Only FAILING tests are printed to stdout.
#
# Usage:
#   ./scripts/run-perf-check-smoke-tests.sh
#
# The following env vars default to the local docker-compose values but can
# be overridden if needed:
#   LAA_SMOKE_ACCESS_API_URL      (default: http://localhost:9080)
#   LAA_SMOKE_ACCESS_DB_URL       (default: jdbc:postgresql://localhost:5432/laa_data_access_api)
#   LAA_SMOKE_ACCESS_DB_USERNAME  (default: laa_user)
#   LAA_SMOKE_ACCESS_DB_PASSWORD  (default: laa_password)
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log()  { echo "[$(date '+%H:%M:%S')] $*"; }

# ---------------------------------------------------------------------------
# Run @SmokeTest-annotated tests in infrastructure mode — capture output
#    and print only failing test summaries.
# ---------------------------------------------------------------------------
cd "${ROOT_DIR}"
log "Running infrastructure smoke tests against performance-check stack (failures only will be shown)..."

GRADLE_LOG=$(mktemp)

set +e
LAA_SMOKE_ACCESS_API_URL="${LAA_SMOKE_ACCESS_API_URL:-http://localhost:9080}" \
LAA_SMOKE_ACCESS_DB_URL="${LAA_SMOKE_ACCESS_DB_URL:-jdbc:postgresql://localhost:5432/laa_data_access_api}" \
LAA_SMOKE_ACCESS_DB_USERNAME="${LAA_SMOKE_ACCESS_DB_USERNAME:-laa_user}" \
LAA_SMOKE_ACCESS_DB_PASSWORD="${LAA_SMOKE_ACCESS_DB_PASSWORD:-laa_password}" \
./gradlew :data-access-service:infrastructureTest \
  --continue \
  --console=plain \
  > "${GRADLE_LOG}" 2>&1
TEST_EXIT_CODE=$?
set -e

if [ "${TEST_EXIT_CODE}" -eq 0 ]; then
  log "Infrastructure smoke tests PASSED — no failures."
else
  log "Infrastructure smoke tests FAILED (exit code ${TEST_EXIT_CODE}). Failures:"
  echo ""
  grep -E "(FAILED|expected:|but was:|org\.[a-z].*Exception|Caused by:)" "${GRADLE_LOG}" || \
    grep -A5 "FAILED" "${GRADLE_LOG}" || \
    tail -30 "${GRADLE_LOG}"
  echo ""
  log "Full report: ${ROOT_DIR}/data-access-service/build/reports/tests/infrastructureTest/index.html"
fi

rm -f "${GRADLE_LOG}"
exit "${TEST_EXIT_CODE}"

