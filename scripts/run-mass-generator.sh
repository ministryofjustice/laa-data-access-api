#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# run-mass-generator.sh
#
# Builds and runs the data-access-mass-generator Spring Boot console app,
# seeding the local PostgreSQL instance with test data.
#
# Usage:
#   ./scripts/run-mass-generator.sh [COUNT]
#
# Examples:
#   ./scripts/run-mass-generator.sh          # generates 100 applications (default)
#   ./scripts/run-mass-generator.sh 5000     # generates 5000 applications
#
# Override database connection details via environment variables:
#   DB_HOST=my-host DB_PORT=5432 ./scripts/run-mass-generator.sh 1000
# ---------------------------------------------------------------------------

COUNT=${1:-100}
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}

# --- Validate COUNT is a positive integer -----------------------------------
if ! [[ "$COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: COUNT must be a positive integer (got: '$COUNT')"
  echo "Usage: $0 [COUNT]"
  exit 1
fi

# --- Check Postgres is reachable --------------------------------------------
echo "Checking PostgreSQL is reachable at ${DB_HOST}:${DB_PORT}..."
if ! nc -z "$DB_HOST" "$DB_PORT" 2>/dev/null; then
  echo "ERROR: Cannot reach PostgreSQL at ${DB_HOST}:${DB_PORT}."
  echo "Make sure the database is running (e.g. 'docker compose up -d') and try again."
  exit 1
fi
echo "PostgreSQL is reachable."

# --- Build the fat JAR ------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Building fat JAR..."
"${PROJECT_ROOT}/gradlew" -p "${PROJECT_ROOT}" :data-access-mass-generator:bootJar

# --- Locate the JAR ---------------------------------------------------------
JAR=$(find "${PROJECT_ROOT}/data-access-mass-generator/build/libs" \
      -name "data-access-mass-generator-*.jar" \
      ! -name "*-plain.jar" \
      | sort | tail -1)

if [[ -z "$JAR" ]]; then
  echo "ERROR: Could not find the data-access-mass-generator JAR after build."
  exit 1
fi

echo "Using JAR: $JAR"

# --- Run the generator ------------------------------------------------------
echo "Starting mass generation of ${COUNT} applications..."
java -jar "$JAR" "$COUNT" \
  --spring.datasource.url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/laa_data_access_api?reWriteBatchedInserts=true"

