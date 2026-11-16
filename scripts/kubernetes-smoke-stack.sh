#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# kubernetes-smoke-stack.sh
#
# Manages a per-run smoke-test stack in an existing Kubernetes namespace:
# an ephemeral PostgreSQL, WireMock SDS and mock OAuth2 server (.helm/smoke-test/stack.yaml)
# plus the API release (.helm/data-access-api with values/uat.yaml and values/smoke-test.yaml).
#
# Usage:
#   RUN_ID=... KUBE_NAMESPACE=... IMAGE_REPOSITORY=... IMAGE_TAG=... AWS_REGION=... \
#     ./scripts/kubernetes-smoke-stack.sh up
#   RUN_ID=... KUBE_NAMESPACE=... ./scripts/kubernetes-smoke-stack.sh down
#   KUBE_NAMESPACE=... ./scripts/kubernetes-smoke-stack.sh cleanup-abandoned [max-age-hours]
#
# Every resource carries laa-smoke-run-id and laa-smoke-created-epoch labels so teardown
# and cleanup only touch resources from smoke-test runs.
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
STACK_TEMPLATE="${ROOT_DIR}/.helm/smoke-test/stack.yaml"
CHART_DIR="${ROOT_DIR}/.helm/data-access-api"
PART_OF="laa-data-access-smoke-test"
RUN_LABEL="laa-smoke-run-id"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ERROR: $*" >&2; exit 1; }

require_var() {
  local name="$1"
  [ -n "${!name:-}" ] || fail "${name} must be set"
}

release_name() {
  echo "smoke-${RUN_ID}"
}

# Labels are applied to ConfigMaps created from files, so they can be removed by run ID.
label_run() {
  local kind="$1" name="$2"
  kubectl -n "${KUBE_NAMESPACE}" label "${kind}" "${name}" \
    "app.kubernetes.io/part-of=${PART_OF}" "${RUN_LABEL}=${RUN_ID}" \
    "laa-smoke-created-epoch=${CREATED_EPOCH}" --overwrite >/dev/null
}

up() {
  require_var KUBE_NAMESPACE
  require_var RUN_ID
  require_var IMAGE_REPOSITORY
  require_var IMAGE_TAG
  require_var AWS_REGION

  local release
  release="$(release_name)"
  CREATED_EPOCH="$(date +%s)"
  local postgres_password
  postgres_password="$(openssl rand -hex 24)"
  echo "::add-mask::${postgres_password}" 2>/dev/null || true

  log "Creating configuration ConfigMaps for ${release}..."
  kubectl -n "${KUBE_NAMESPACE}" create configmap "${release}-mock-oauth2-config" \
    --from-file=config.json="${ROOT_DIR}/infra/mock-oauth2/config-smoke-test.json" \
    --dry-run=client -o yaml | kubectl apply -f -
  label_run configmap "${release}-mock-oauth2-config"

  kubectl -n "${KUBE_NAMESPACE}" create configmap "${release}-sds-mappings" \
    --from-file="${ROOT_DIR}/infra/wiremock/sds/mappings" \
    --dry-run=client -o yaml | kubectl apply -f -
  label_run configmap "${release}-sds-mappings"

  log "Applying PostgreSQL, mock OAuth2 and SDS WireMock for ${release}..."
  RELEASE_NAME="${release}" RUN_ID="${RUN_ID}" CREATED_EPOCH="${CREATED_EPOCH}" \
    POSTGRES_DB="smoke" POSTGRES_USER="smoke_user" POSTGRES_PASSWORD="${postgres_password}" \
    envsubst '${RELEASE_NAME} ${RUN_ID} ${CREATED_EPOCH} ${POSTGRES_DB} ${POSTGRES_USER} ${POSTGRES_PASSWORD}' \
    < "${STACK_TEMPLATE}" \
    | kubectl -n "${KUBE_NAMESPACE}" apply -f -

  for deployment in smoke-postgres mock-oauth2 sds; do
    kubectl -n "${KUBE_NAMESPACE}" rollout status "deployment/${release}-${deployment}" --timeout=180s \
      || fail "${deployment} did not become ready for ${release}."
  done

  log "Installing API release ${release}..."
  helm upgrade --install "${release}" "${CHART_DIR}" \
    --namespace "${KUBE_NAMESPACE}" \
    --values "${CHART_DIR}/values/uat.yaml" \
    --values "${CHART_DIR}/values/smoke-test.yaml" \
    --set image.repository="${IMAGE_REPOSITORY}" \
    --set image.tag="${IMAGE_TAG}" \
    --set spring.profile="smoke" \
    --set aws.region="${AWS_REGION}" \
    --set mockOAuth2.sharedInstance.serviceName="${release}-mock-oauth2" \
    --set mockOAuth2.sharedInstance.namespace="${KUBE_NAMESPACE}" \
    --wait --timeout 5m \
    || fail "Helm install of ${release} failed."
  log "Smoke stack ${release} is ready."
}

# Removes every resource for one run. Safe to call when some resources are missing.
down() {
  require_var KUBE_NAMESPACE
  require_var RUN_ID
  local release
  release="$(release_name)"

  log "Uninstalling API release ${release}..."
  helm uninstall "${release}" --namespace "${KUBE_NAMESPACE}" --ignore-not-found --wait --timeout 3m \
    || log "WARNING: helm uninstall failed for ${release}."

  log "Deleting labelled resources for run ${RUN_ID}..."
  kubectl -n "${KUBE_NAMESPACE}" delete \
    deployments,services,secrets,configmaps,pods,networkpolicies \
    -l "app.kubernetes.io/part-of=${PART_OF},${RUN_LABEL}=${RUN_ID}" \
    --ignore-not-found --wait=false \
    || log "WARNING: resource deletion failed for run ${RUN_ID}."
}

# Removes stack resources and Helm releases created by smoke-test runs older than the threshold.
cleanup_abandoned() {
  require_var KUBE_NAMESPACE
  local max_age_hours="${1:-6}"
  local cutoff
  cutoff=$(( $(date +%s) - max_age_hours * 3600 ))
  log "Removing smoke-test resources created before epoch ${cutoff}..."

  local run_ids
  run_ids="$(kubectl -n "${KUBE_NAMESPACE}" get deployments,services,secrets,configmaps \
    -l "app.kubernetes.io/part-of=${PART_OF}" \
    -o jsonpath='{range .items[*]}{.metadata.labels.laa-smoke-run-id}{" "}{.metadata.labels.laa-smoke-created-epoch}{"\n"}{end}' \
    | sort -u)"

  local run_id created
  while read -r run_id created; do
    [ -n "${run_id}" ] && [ -n "${created}" ] || continue
    if [ "${created}" -lt "${cutoff}" ]; then
      log "Cleaning abandoned run ${run_id} (created ${created})."
      RUN_ID="${run_id}" down
    fi
  done <<< "${run_ids}"
  log "Cleanup complete."
}

# Dispatch
case "${1:-}" in
  up) up ;;
  down) down ;;
  cleanup-abandoned) shift; cleanup_abandoned "${1:-}" ;;
  *) fail "Usage: $0 {up|down|cleanup-abandoned [max-age-hours]}" ;;
esac
