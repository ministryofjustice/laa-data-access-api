#!/bin/sh
set -e

# Smoke test runner script
# Handles constructing the database URL for RDS deployments where
# the URL components come from separate environment variables.

echo "=== Smoke Test Runner ==="
echo "API URL: $LAA_SMOKE_ACCESS_API_URL"
echo "DB Flavour: $DB_FLAVOUR"

# For RDS deployments, construct the DB URL from components
if [ "$DB_FLAVOUR" = "rds" ]; then
  if [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ]; then
    echo "ERROR: DB_HOST and DB_NAME must be set for RDS deployments"
    exit 1
  fi
  export LAA_SMOKE_ACCESS_DB_URL="jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}"
  echo "Constructed DB URL: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}"
else
  echo "DB URL: $LAA_SMOKE_ACCESS_DB_URL"
fi

echo "DB Username: $LAA_SMOKE_ACCESS_DB_USERNAME"
echo "========================="
echo ""

# Run the infrastructure tests
exec ./gradlew --no-daemon :data-access-service:infrastructureTest --console=plain "$@"

