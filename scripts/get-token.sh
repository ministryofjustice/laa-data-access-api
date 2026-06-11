#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# get-token.sh
#
# Fetches a test JWT from the mock-oauth2-server for the specified
# environment and optionally copies it to the clipboard.
#
# Usage:
#   ./scripts/get-token.sh [environment] [options]
#
# Environments:
#   local       Local development (default) — mock server on port 9999
#   smoke       Smoke test infrastructure   — mock server on port 9998
#   uat         UAT mock server via kubectl port-forward on port 9999
#   custom      Use OAUTH_TOKEN_URL env var  — bring your own URL
#
# Options:
#   -c, --copy      Copy the token to the clipboard (macOS: pbcopy)
#   -d, --decode    Decode and pretty-print the token payload
#   -h, --help      Show this help text
#
# Examples:
#   ./scripts/get-token.sh
#   ./scripts/get-token.sh local --copy
#   ./scripts/get-token.sh smoke --decode
#   ./scripts/get-token.sh uat --decode
#   OAUTH_TOKEN_URL=http://localhost:7777/entra/token ./scripts/get-token.sh custom
#
# Environment variable overrides (all optional):
#   OAUTH_TOKEN_URL   Full token URL (used with the 'custom' environment)
#   OAUTH_LOCAL_PORT  Local port used for UAT mock-oauth2 port-forward (default: 9999)
#   OAUTH_ISSUER_HOST Host header used when token URL is port-forwarded but the issuer must be
#                      the in-cluster mock-oauth2 service
#   OAUTH_CLIENT_ID   OAuth client_id   (default: test)
#   OAUTH_CLIENT_SECRET  OAuth client_secret  (default: test)
#   OAUTH_SCOPE       OAuth scope  (default: api://laa-data-access-api/.default)
# ---------------------------------------------------------------------------

set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
ENV="${1:-local}"
COPY=false
DECODE=false

# Shift past the first positional arg (environment) if it's not a flag
if [[ "${ENV}" != -* ]]; then
  shift || true
else
  ENV="local"
fi

# Parse remaining options
for arg in "$@"; do
  case "${arg}" in
    -c|--copy)   COPY=true ;;
    -d|--decode) DECODE=true ;;
    -h|--help)
      sed -n '2,/^# ---/p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}" >&2
      echo "Run ./scripts/get-token.sh --help for usage." >&2
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Resolve token URL
# ---------------------------------------------------------------------------
case "${ENV}" in
  local)
    TOKEN_URL="${OAUTH_TOKEN_URL:-http://localhost:9999/entra/token}"
    ENV_LABEL="local development"
    ;;
  smoke)
    TOKEN_URL="${OAUTH_TOKEN_URL:-http://localhost:9998/entra/token}"
    ENV_LABEL="smoke test"
    ;;
  uat)
    TOKEN_URL="${OAUTH_TOKEN_URL:-http://localhost:${OAUTH_LOCAL_PORT:-9999}/entra/token}"
    OAUTH_ISSUER_HOST="${OAUTH_ISSUER_HOST:-spike-dstew1360-data-access-api-mock-oauth2:9999}"
    ENV_LABEL="UAT mock-oauth2 via port-forward"
    ;;
  custom)
    if [[ -z "${OAUTH_TOKEN_URL:-}" ]]; then
      echo "ERROR: 'custom' environment requires OAUTH_TOKEN_URL to be set." >&2
      echo "  e.g.  OAUTH_TOKEN_URL=http://localhost:7777/entra/token ./scripts/get-token.sh custom" >&2
      exit 1
    fi
    TOKEN_URL="${OAUTH_TOKEN_URL}"
    ENV_LABEL="custom (${TOKEN_URL})"
    ;;
  *)
    echo "ERROR: Unknown environment '${ENV}'. Valid values: local, smoke, uat, custom." >&2
    exit 1
    ;;
esac

CLIENT_ID="${OAUTH_CLIENT_ID:-test}"
CLIENT_SECRET="${OAUTH_CLIENT_SECRET:-test}"
SCOPE="${OAUTH_SCOPE:-api://laa-data-access-api/.default}"

# ---------------------------------------------------------------------------
# Dependency check
# ---------------------------------------------------------------------------
if ! command -v curl &>/dev/null; then
  echo "ERROR: curl is required but not installed." >&2
  exit 1
fi

if ! command -v jq &>/dev/null; then
  echo "ERROR: jq is required but not installed." >&2
  echo "  macOS:  brew install jq" >&2
  echo "  Linux:  apt-get install jq  /  yum install jq" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Ensure mock OAuth server is running
# ---------------------------------------------------------------------------
ensure_mock_server_running() {
  local container="$1"
  local compose_file="$2"
  local service="$3"

  if ! command -v docker &>/dev/null; then
    echo "⚠ docker not found — cannot auto-start mock server. Proceeding anyway..." >&2
    return
  fi

  local state
  state=$(docker inspect --format '{{.State.Status}}' "${container}" 2>/dev/null || echo "missing")

  case "${state}" in
    running)
      echo "✓ Mock OAuth server already running (${container})" >&2
      ;;
    exited|paused|created)
      echo "⚠ Mock OAuth server container exists but is not running — starting it..." >&2
      docker start "${container}" >/dev/null
      echo "✓ Started ${container}" >&2
      ;;
    missing)
      echo "⚠ Mock OAuth server not found — starting via docker compose..." >&2
      docker compose -f "${compose_file}" up -d "${service}" >/dev/null
      echo "✓ Started ${service} from ${compose_file}" >&2
      ;;
    *)
      echo "⚠ Mock OAuth server is in unexpected state '${state}' — attempting to start..." >&2
      docker compose -f "${compose_file}" up -d "${service}" >/dev/null
      ;;
  esac

  # Brief wait for the server to become ready
  local retries=10
  echo "  Waiting for mock server to be ready..." >&2
  while (( retries-- > 0 )); do
    if curl -sf "${TOKEN_URL%/token}/jwks" >/dev/null 2>&1; then
      echo "✓ Mock server is ready" >&2
      return
    fi
    sleep 1
  done
  echo "⚠ Mock server did not become ready in time — attempting token fetch anyway..." >&2
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

case "${ENV}" in
  local)
    ensure_mock_server_running \
      "laa-mock-oauth2" \
      "${ROOT_DIR}/docker-compose.yml" \
      "mock-oauth2-server"
    ;;
  smoke)
    ensure_mock_server_running \
      "laa-mock-oauth2-smoketest" \
      "${ROOT_DIR}/docker-compose.smoke-test.yml" \
      "mock-oauth2-server-smoketest"
    ;;
esac

# ---------------------------------------------------------------------------
# Fetch token
# ---------------------------------------------------------------------------
echo "Fetching token from ${ENV_LABEL}..." >&2
echo "  URL: ${TOKEN_URL}" >&2
if [[ -n "${OAUTH_ISSUER_HOST:-}" ]]; then
  echo "  Issuer host: ${OAUTH_ISSUER_HOST}" >&2
fi

CURL_ARGS=(
  -s -X POST "${TOKEN_URL}"
  -H "Content-Type: application/x-www-form-urlencoded"
)

if [[ -n "${OAUTH_ISSUER_HOST:-}" ]]; then
  CURL_ARGS+=(
    -H "Host: ${OAUTH_ISSUER_HOST}"
  )
fi

CURL_ARGS+=(
  -d "grant_type=client_credentials"
  -d "client_id=${CLIENT_ID}"
  -d "client_secret=${CLIENT_SECRET}"
  -d "scope=${SCOPE}"
)

HTTP_BODY=$(curl "${CURL_ARGS[@]}")
HTTP_STATUS=$(curl "${CURL_ARGS[@]}" -o /dev/null -w "%{http_code}")

if [[ "${HTTP_STATUS}" != "200" ]]; then
  echo "ERROR: Token request failed (HTTP ${HTTP_STATUS})." >&2
  echo "  Response: ${HTTP_BODY}" >&2
  echo "" >&2
  echo "Is the mock OAuth server running?" >&2
  case "${ENV}" in
    local) echo "  Start it: docker compose up -d mock-oauth2-server" >&2 ;;
    smoke) echo "  Start it: docker compose -f docker-compose.smoke-test.yml up -d" >&2 ;;
  esac
  exit 1
fi

TOKEN=$(echo "${HTTP_BODY}" | jq -r '.access_token // empty')

if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: Response did not contain an access_token." >&2
  echo "  Response: ${HTTP_BODY}" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
echo "" >&2
echo "✓ Token obtained successfully" >&2

if ${COPY}; then
  if command -v pbcopy &>/dev/null; then
    echo "${TOKEN}" | pbcopy
    echo "✓ Copied to clipboard" >&2
  elif command -v xclip &>/dev/null; then
    echo "${TOKEN}" | xclip -selection clipboard
    echo "✓ Copied to clipboard" >&2
  else
    echo "⚠ Could not copy to clipboard (pbcopy / xclip not found)" >&2
  fi
fi

if ${DECODE}; then
  echo "" >&2
  echo "── Token payload ──────────────────────────────────────────────────────" >&2
  echo "${TOKEN}" | awk -F. '{print $2}' | base64 -d 2>/dev/null | jq >&2
  echo "───────────────────────────────────────────────────────────────────────" >&2
fi

echo "" >&2
echo "To use this token:" >&2
echo "  export TOKEN='${TOKEN}'" >&2
echo "  curl http://localhost:8080/api/v0/caseworkers \\" >&2
echo "    -H \"Authorization: Bearer \$TOKEN\" \\" >&2
echo "    -H \"X-Service-Name: CIVIL_APPLY\"" >&2
echo "" >&2

# Print the raw token to stdout so callers can capture it:
#   TOKEN=$(./scripts/get-token.sh local)
echo "${TOKEN}"
