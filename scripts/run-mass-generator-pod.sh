#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${KUBE_NAMESPACE:?Must set KUBE_NAMESPACE}"
RELEASE="${RELEASE_NAME:? Must set RELEASE_NAME}"
DEPLOYMENT="${RELEASE_NAME}-mass-generator"
LABEL_SELECTOR="app.kubernetes.io/name=mass-generator,app.kubernetes.io/instance=${RELEASE}"

echo "==> Scaling up ${DEPLOYMENT} in namespace ${NAMESPACE}..."
kubectl -n "$NAMESPACE" scale deployment "$DEPLOYMENT" --replicas=1

echo "==> Waiting for pod to be ready..."
kubectl -n "$NAMESPACE" wait --for=condition=ready pod \
  -l "$LABEL_SELECTOR" \
  --timeout=120s

POD=$(kubectl -n "$NAMESPACE" get pod -l "$LABEL_SELECTOR" -o jsonpath='{.items[0].metadata.name}')
echo "==> Pod is ready: ${POD}"

cleanup() {
  echo ""
  echo "==> Scaling down ${DEPLOYMENT}..."
  kubectl -n "$NAMESPACE" scale deployment "$DEPLOYMENT" --replicas=0
  echo "==> Done."
}
trap cleanup EXIT

echo ""
echo "==> Execing into ${POD}. Run: java -jar mass-generator.jar"
echo "==> The pod will be scaled down automatically when you exit."
echo ""
kubectl -n "$NAMESPACE" exec -it "$POD" -- /bin/sh

