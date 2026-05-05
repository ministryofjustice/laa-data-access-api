#!/usr/bin/env bash
set -euo pipefail

# Runs mass generator by executing curl inside a Kubernetes pod (no port-forward needed)
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

POD_NAME=$(kubectl get pods -n "${NAMESPACE}" -l app=mass-generator -o jsonpath='{.items[0].metadata.name}')

if [ -z "${POD_NAME}" ]; then
  echo "ERROR: No mass-generator pod found in namespace ${NAMESPACE}"
  echo "Listing all pods:"
  kubectl get pods -n "${NAMESPACE}"
  exit 1
fi

echo "Found pod: ${POD_NAME}"
echo ""

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
