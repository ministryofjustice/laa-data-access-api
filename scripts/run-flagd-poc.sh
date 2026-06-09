#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-flagd-poc.sh
#
# Small helper for the local flagd + OpenFeature POC.
# - Starts/stops local flagd (JSON-backed)
# - Optionally runs the Spring Boot app with flagd env vars
# - Optionally calls /flags if the app is already running
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.flagd-poc.yml"

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-flagd-poc.sh start      # start local flagd container
  ./scripts/run-flagd-poc.sh stop       # stop local flagd container
  ./scripts/run-flagd-poc.sh restart    # restart local flagd container
  ./scripts/run-flagd-poc.sh logs       # tail flagd logs
  ./scripts/run-flagd-poc.sh status     # show flagd container status
  ./scripts/run-flagd-poc.sh run-app    # run Spring Boot with flagd enabled
  ./scripts/run-flagd-poc.sh check      # call local /flags endpoint

Notes:
  - 'run-app' expects local app prerequisites (DB, credentials, etc.) to be available.
  - 'check' expects the app to be running at http://localhost:8080.
EOF
}

cmd="${1:-start}"

case "${cmd}" in
  start)
    log "Starting local flagd from ${COMPOSE_FILE}"
    docker compose -f "${COMPOSE_FILE}" up -d flagd
    log "flagd started on localhost:8013"
    ;;

  stop)
    log "Stopping local flagd"
    docker compose -f "${COMPOSE_FILE}" down
    ;;

  restart)
    "${BASH_SOURCE[0]}" stop
    "${BASH_SOURCE[0]}" start
    ;;

  logs)
    docker compose -f "${COMPOSE_FILE}" logs -f flagd
    ;;

  status)
    docker compose -f "${COMPOSE_FILE}" ps
    ;;

  run-app)
    log "Running Spring Boot app with flagd enabled"
    cd "${ROOT_DIR}"
    FLAGD_ENABLED=true \
    FLAGD_HOST=localhost \
    FLAGD_PORT=8013 \
    ./gradlew :data-access-service:bootRun
    ;;

  check)
    log "Calling local /flags endpoint"
    curl --fail --silent --show-error http://localhost:8080/flags
    echo
    ;;

  help|-h|--help)
    usage
    ;;

  *)
    echo "Unknown command: ${cmd}" >&2
    usage
    exit 1
    ;;
esac

