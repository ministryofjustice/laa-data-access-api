#!/usr/bin/env bash
set -euo pipefail

# Runs mass generator by executing in a Kubernetes pod
# Usage: ./scripts/run-mass-generator-pod.sh [namespace] [count] [cleanup]

NAMESPACE="${1:-laa-data-access-dev}"
COUNT="${2:-1000}"
CLEANUP="${3:-false}"

echo "========================================="
echo "  Mass Generator - Pod Execution"
echo "========================================="
echo "Namespace: ${NAMESPACE}"
echo "Count:     ${COUNT}"
echo "Cleanup:   ${CLEANUP}"
echo "========================================="

POD_NAME=$(kubectl get pods -n "${NAMESPACE}" -l app=laa-data-access-api -o jsonpath='{.items[0].metadata.name}')

if [ -z "${POD_NAME}" ]; then
  echo "ERROR: No laa-data-access-api pod found in namespace ${NAMESPACE}"
  exit 1
fi

echo "Found pod: ${POD_NAME}"
echo ""
echo "Starting mass generation job..."

JOB_RESPONSE=$(kubectl exec -n "${NAMESPACE}" "${POD_NAME}" -- \
  curl -s -X POST "http://localhost:8080/api/mass-generator/generate?count=${COUNT}&cleanup=${CLEANUP}")

echo "${JOB_RESPONSE}" | jq '.'

JOB_ID=$(echo "${JOB_RESPONSE}" | jq -r '.jobId')

if [ -z "${JOB_ID}" ] || [ "${JOB_ID}" = "null" ]; then
  echo "ERROR: Failed to start job"
  exit 1
fi

echo ""
echo "Job started: ${JOB_ID}"
echo "Monitoring (Ctrl+C to stop)..."
echo ""

while true; do
  STATUS_JSON=$(kubectl exec -n "${NAMESPACE}" "${POD_NAME}" -- \
    curl -s "http://localhost:8080/api/mass-generator/jobs/${JOB_ID}")

  STATUS=$(echo "${STATUS_JSON}" | jq -r '.status')
  PROCESSED=$(echo "${STATUS_JSON}" | jq -r '.processedCount')
  TARGET=$(echo "${STATUS_JSON}" | jq -r '.targetCount')

  echo "[$(date +%H:%M:%S)] Status: ${STATUS} | Progress: ${PROCESSED}/${TARGET}"

  if [ "${STATUS}" = "COMPLETED" ] || [ "${STATUS}" = "FAILED" ] || [ "${STATUS}" = "CANCELLED" ]; then
    echo ""
    echo "========================================="
    echo "Job finished: ${STATUS}"
    echo "========================================="
    echo "${STATUS_JSON}" | jq '.'
    break
  fi

  sleep 5
done
