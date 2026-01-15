#!/usr/bin/env bash
set -euo pipefail

# Init script for LocalStack: creates S3 bucket and DynamoDB table used by the app.
# This script is mounted into /etc/localstack/init/ready.d and executed after LocalStack starts.

ENDPOINT="http://localhost:4566"
REGION="eu-west-2"
BUCKET="app-history-payloads"
TABLE="EventIndexTable"

# Prefer awslocal if available inside the container, otherwise fall back to aws CLI
if command -v awslocal >/dev/null 2>&1; then
  AWS_CMD="awslocal"
else
  AWS_CMD="aws --endpoint-url=${ENDPOINT} --region ${REGION}"
fi

echo "[localstack-init] Using command: ${AWS_CMD}"

# Helper: wait for a command to succeed, with timeout
wait_for_cmd() {
  local cmd="$1"
  local retries=${2:-30}
  local delay=${3:-1}
  local i=0
  until sh -c "$cmd" >/dev/null 2>&1; do
    i=$((i+1))
    if [ "$i" -ge "$retries" ]; then
      echo "[localstack-init] Timeout waiting for: $cmd" >&2
      return 1
    fi
    sleep $delay
  done
  return 0
}

# Wait for LocalStack edge to be healthy
if ! wait_for_cmd "curl -sS ${ENDPOINT}/health >/dev/null 2>&1" 30 1; then
  echo "[localstack-init] LocalStack health check failed; continuing anyway"
fi

# Wait for DynamoDB service to be ready (so it has loaded persisted state)
# We try listing tables until DynamoDB responds (no error). This avoids a race where describe-table fails
# because DynamoDB hasn't loaded its persisted DB yet.
if ! wait_for_cmd "${AWS_CMD} dynamodb list-tables >/dev/null 2>&1" 60 1; then
  echo "[localstack-init] DynamoDB did not become ready in time; continuing"
fi

# Create S3 bucket if missing
if ${AWS_CMD} s3api head-bucket --bucket "${BUCKET}" >/dev/null 2>&1; then
  echo "[localstack-init] Bucket ${BUCKET} already exists"
else
  echo "[localstack-init] Creating bucket ${BUCKET}"
  # Try create with LocationConstraint then fall back to default create
  ${AWS_CMD} s3api create-bucket --bucket "${BUCKET}" --create-bucket-configuration LocationConstraint=${REGION} >/dev/null 2>&1 || \
    ${AWS_CMD} s3api create-bucket --bucket "${BUCKET}" >/dev/null 2>&1 || true
  echo "[localstack-init] Bucket created"
fi

# Create DynamoDB table if missing
if ${AWS_CMD} dynamodb describe-table --table-name "${TABLE}" >/dev/null 2>&1; then
  echo "[localstack-init] Table ${TABLE} already exists"
else
  echo "[localstack-init] Creating DynamoDB table ${TABLE} (pk/sk)"
  ${AWS_CMD} dynamodb create-table \
    --table-name "${TABLE}" \
    --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
    --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
    --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 >/dev/null 2>&1 || true
  echo "[localstack-init] Table created"
fi

echo "[localstack-init] Initialization complete"

