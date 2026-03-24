#!/usr/bin/env bash
# run-mass-generator.sh
#
# Builds and runs the LAA Data Access mass data generator.
#
# Usage:
#   ./scripts/run-mass-generator.sh [COUNT]
#
# Arguments:
#   COUNT   Number of application records to generate (default: 100)
#
# Prerequisites:
#   - Docker must be running with the Postgres container up
#     (start it with: docker compose up -d)
#   - Java 25+ must be available on PATH

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
MODULE="data-access-mass-generator"
COUNT="${1:-100}"

# ── Validate count argument ──────────────────────────────────────────────────
if ! [[ "${COUNT}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: COUNT must be a positive integer, got: '${COUNT}'" >&2
  exit 1
fi

echo "==> Repository root : ${REPO_ROOT}"
echo "==> Records to generate: ${COUNT}"

# ── Check Postgres is reachable ──────────────────────────────────────────────
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

if ! nc -z "${DB_HOST}" "${DB_PORT}" 2>/dev/null; then
  echo ""
  echo "WARNING: Cannot reach Postgres at ${DB_HOST}:${DB_PORT}."
  echo "         Start the database with:  docker compose up -d"
  echo "         Then re-run this script."
  exit 1
fi

echo "==> Postgres is up at ${DB_HOST}:${DB_PORT}"

# ── Build the fat JAR ────────────────────────────────────────────────────────
echo ""
echo "==> Building ${MODULE} bootJar..."
cd "${REPO_ROOT}"
./gradlew ":${MODULE}:bootJar" --quiet

# Locate the built JAR (glob-safe: picks the first matching file)
JAR_PATH="$(ls "${REPO_ROOT}/${MODULE}/build/libs/${MODULE}-"*.jar 2>/dev/null | head -n 1)"

if [[ -z "${JAR_PATH}" ]]; then
  echo "ERROR: Could not find built JAR under ${REPO_ROOT}/${MODULE}/build/libs/" >&2
  exit 1
fi

echo "==> JAR: ${JAR_PATH}"

# ── Run the generator ────────────────────────────────────────────────────────
echo ""
echo "==> Starting mass data generator (COUNT=${COUNT})..."
echo "    Press Ctrl-C to abort."
echo ""

java -jar "${JAR_PATH}" "${COUNT}"

echo ""
echo "==> Done."

