#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-infrastructure-smoke-tests.sh
#
# Builds the application JAR, starts the full local infrastructure via
# docker-compose.smoke-test.yml (Postgres + the app itself), runs all tests
# annotated with @SmokeTest in infrastructure mode, then tears everything
# down — whether the tests pass or fail.
#
# Usage:
#   ./scripts/run-infrastructure-smoke-tests.sh
#
# The following env vars default to the local docker-compose values but can
# be overridden if needed:
#   LAA_SMOKE_ACCESS_API_URL      (default: http://localhost:9000)
#   LAA_SMOKE_ACCESS_DB_URL       (default: jdbc:postgresql://localhost:6432/laa_data_access_api)
#   LAA_SMOKE_ACCESS_DB_USERNAME  (default: laa_user)
#   LAA_SMOKE_ACCESS_DB_PASSWORD  (default: laa_password)
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.smoke-test.yml"
COMPOSE_PROJECT="laa-data-access-smoke-test"

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
  docker compose -f "${COMPOSE_FILE}" -p "${COMPOSE_PROJECT}" down --volumes --remove-orphans || true
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
log "Starting local infrastructure (docker-compose.smoke-test.yml)..."
docker compose -f "${COMPOSE_FILE}" -p "${COMPOSE_PROJECT}" up --build --detach --wait \
  || fail "docker compose failed to start all services in a healthy state."
log "All services are healthy."

# Wait for Tomcat to accept real requests (healthcheck alone is not sufficient)
log "Waiting for app to accept traffic..."
READINESS_URL="${LAA_SMOKE_ACCESS_API_URL:-http://localhost:9000}/api/v0/caseworkers"
for i in $(seq 1 120); do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Service-Name: CIVIL_APPLY" \
    "${READINESS_URL}" 2>/dev/null) || true
  if [ "${HTTP_CODE:-000}" != "000" ]; then
    log "App ready (HTTP ${HTTP_CODE})."
    break
  fi
  [ "${i}" -eq 120 ] && fail "App did not become ready within 120 seconds."
  sleep 1
done

# ---------------------------------------------------------------------------
# 3. Run @SmokeTest-annotated tests in infrastructure mode
#    LAA_ACCESS_* env vars are read by InfrastructureTestContextProvider.
#    The infrastructureTest Gradle task sets test.mode=infrastructure
#    unconditionally, so HarnessExtension selects InfrastructureTestContextProvider
#    and the includeTags('smoke') filter ensures only @SmokeTest tests execute.
# ---------------------------------------------------------------------------
log "Running infrastructure smoke tests..."
LAA_SMOKE_ACCESS_API_URL="${LAA_SMOKE_ACCESS_API_URL:-http://localhost:9000}" \
LAA_SMOKE_ACCESS_DB_URL="${LAA_SMOKE_ACCESS_DB_URL:-jdbc:postgresql://localhost:6432/laa_data_access_api}" \
LAA_SMOKE_ACCESS_DB_USERNAME="${LAA_SMOKE_ACCESS_DB_USERNAME:-laa_user}" \
LAA_SMOKE_ACCESS_DB_PASSWORD="${LAA_SMOKE_ACCESS_DB_PASSWORD:-laa_password}" \
./gradlew :data-access-service:infrastructureTest \
  --continue \
  --console=plain \
  || true
TEST_EXIT_CODE=$?

if [ "${TEST_EXIT_CODE}" -eq 0 ]; then
  log "Infrastructure smoke tests PASSED."
else
  log "Infrastructure smoke tests FAILED (exit code ${TEST_EXIT_CODE})."
  log "Full report: ${ROOT_DIR}/data-access-service/build/reports/tests/infrastructureTest/index.html"
fi

# Exit with the test exit code; the trap will handle teardown.
exit "${TEST_EXIT_CODE}"

