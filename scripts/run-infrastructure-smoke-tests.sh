#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-infrastructure-smoke-tests.sh
#
# Builds the application JAR, starts the full local infrastructure via
# docker-compose.local.yml (Postgres + the app itself), runs all tests
# annotated with @SmokeTest in infrastructure mode, then tears everything
# down — whether the tests pass or fail.
#
# Usage:
#   ./scripts/run-infrastructure-smoke-tests.sh
#
# The following env vars default to the local docker-compose values but can
# be overridden if needed:
#   LAA_ACCESS_API_URL      (default: http://localhost:8080)
#   LAA_ACCESS_DB_URL       (default: jdbc:postgresql://localhost:5432/laa_data_access_api)
#   LAA_ACCESS_DB_USERNAME  (default: laa_user)
#   LAA_ACCESS_DB_PASSWORD  (default: laa_password)
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.local.yml"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log()  { echo "[$(date '+%H:%M:%S')] $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ERROR: $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# Tear-down: always runs on EXIT
# ---------------------------------------------------------------------------
teardown() {
  local exit_code=$?
  log "Tearing down local infrastructure..."
  docker compose -f "${COMPOSE_FILE}" down --volumes --remove-orphans || true
  log "Infrastructure stopped."
  exit "${exit_code}"
}
trap teardown EXIT

# ---------------------------------------------------------------------------
# 1. Build the application JAR (required by the Dockerfile COPY step)
# ---------------------------------------------------------------------------
log "Building application JAR..."
cd "${ROOT_DIR}"
./gradlew :data-access-service:bootJar --quiet \
  || fail "Gradle bootJar build failed."
log "JAR built successfully."

# ---------------------------------------------------------------------------
# 2. Start full infrastructure and wait until all services are healthy
# ---------------------------------------------------------------------------
log "Starting local infrastructure (docker-compose.local.yml)..."
docker compose -f "${COMPOSE_FILE}" up --build --detach --wait \
  || fail "docker compose failed to start all services in a healthy state."
log "All services are healthy."

# ---------------------------------------------------------------------------
# 3. Run @SmokeTest-annotated tests in infrastructure mode
#    LAA_ACCESS_* env vars are read by InfrastructureTestContextProvider.
#    -Dtest.mode=infrastructure signals HarnessExtension to use
#    InfrastructureTestContextProvider and skip any tests not annotated
#    with @SmokeTest.
#
#    NOTE: --tests is scoped to harness-based test classes for now because
#    not all integration tests extend BaseHarnessTest yet. Expand the filter
#    as more tests are ported to the harness.
# ---------------------------------------------------------------------------
log "Running infrastructure smoke tests..."
LAA_ACCESS_API_URL="${LAA_ACCESS_API_URL:-http://localhost:8080}" \
LAA_ACCESS_DB_URL="${LAA_ACCESS_DB_URL:-jdbc:postgresql://localhost:5432/laa_data_access_api}" \
LAA_ACCESS_DB_USERNAME="${LAA_ACCESS_DB_USERNAME:-laa_user}" \
LAA_ACCESS_DB_PASSWORD="${LAA_ACCESS_DB_PASSWORD:-laa_password}" \
./gradlew :data-access-service:integrationTest \
  -Dtest.mode=infrastructure \
  --tests "uk.gov.justice.laa.dstew.access.controller.caseworker.GetCaseworkersTest" \
  --tests "uk.gov.justice.laa.dstew.access.controller.application.GetApplicationTest"
TEST_EXIT_CODE=$?

if [ "${TEST_EXIT_CODE}" -eq 0 ]; then
  log "Infrastructure smoke tests PASSED."
else
  log "Infrastructure smoke tests FAILED (exit code ${TEST_EXIT_CODE})."
fi

# Exit with the test exit code; the trap will handle teardown.
exit "${TEST_EXIT_CODE}"

