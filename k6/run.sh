#!/usr/bin/env sh
set -eu

pack_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$pack_dir/.." && pwd)
scenario=${1:-scenarios/runtime-smoke.js}
if [ "$#" -gt 0 ]; then
  shift
fi

case "$scenario" in
  /*) scenario_path=$scenario ;;
  *) scenario_path="$pack_dir/$scenario" ;;
esac

if [ ! -f "$scenario_path" ]; then
  echo "k6 scenario not found: $scenario" >&2
  exit 64
fi

: "${K6_SUMMARY_PATH:=k6/results/summary.json}"
case "$K6_SUMMARY_PATH" in
  /*|*../*|..)
    echo "K6_SUMMARY_PATH must be a relative path inside the repository." >&2
    exit 64
    ;;
esac
export K6_SUMMARY_PATH
summary_dir=$(dirname "$K6_SUMMARY_PATH")
mkdir -p "$repo_dir/$summary_dir"

if command -v k6 >/dev/null 2>&1; then
  exec k6 run \
    -e "K6_API_BASE_URL=${K6_API_BASE_URL-}" \
    -e "K6_OAUTH_TOKEN_URL=${K6_OAUTH_TOKEN_URL-}" \
    -e "K6_SERVICE_NAME=${K6_SERVICE_NAME-}" \
    -e "K6_PROVIDER_CLIENT_ID=${K6_PROVIDER_CLIENT_ID-}" \
    -e "K6_PROVIDER_CLIENT_SECRET=${K6_PROVIDER_CLIENT_SECRET-}" \
    -e "K6_APPLICATION_CASEWORKER_CLIENT_ID=${K6_APPLICATION_CASEWORKER_CLIENT_ID-}" \
    -e "K6_APPLICATION_CASEWORKER_CLIENT_SECRET=${K6_APPLICATION_CASEWORKER_CLIENT_SECRET-}" \
    -e "K6_PA_CASEWORKER_CLIENT_ID=${K6_PA_CASEWORKER_CLIENT_ID-}" \
    -e "K6_PA_CASEWORKER_CLIENT_SECRET=${K6_PA_CASEWORKER_CLIENT_SECRET-}" \
    -e "K6_CONTENDER_CLIENT_ID=${K6_CONTENDER_CLIENT_ID-}" \
    -e "K6_CONTENDER_CLIENT_SECRET=${K6_CONTENDER_CLIENT_SECRET-}" \
    -e "K6_POLL_TIMEOUT_MS=${K6_POLL_TIMEOUT_MS-}" \
    -e "K6_POLL_INTERVAL_MS=${K6_POLL_INTERVAL_MS-}" \
    -e "K6_SUMMARY_PATH=$K6_SUMMARY_PATH" \
    -e "K6_CONTENTION_VUS=${K6_CONTENTION_VUS-}" \
    -e "K6_PERFORMANCE_VUS=${K6_PERFORMANCE_VUS-}" \
    -e "K6_PERFORMANCE_DURATION=${K6_PERFORMANCE_DURATION-}" \
    -e "K6_THRESHOLDS=${K6_THRESHOLDS-}" \
    "$scenario_path" "$@"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Neither k6 nor Docker is installed; install one to run the harness." >&2
  exit 69
fi

environment_file=$(mktemp)
trap 'rm -f "$environment_file"' EXIT HUP INT TERM
env > "$environment_file"

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  --env-file "$environment_file" \
  -e K6_API_BASE_URL \
  -e K6_OAUTH_TOKEN_URL \
  -e K6_SERVICE_NAME \
  -e K6_PROVIDER_CLIENT_ID \
  -e K6_PROVIDER_CLIENT_SECRET \
  -e K6_APPLICATION_CASEWORKER_CLIENT_ID \
  -e K6_APPLICATION_CASEWORKER_CLIENT_SECRET \
  -e K6_PA_CASEWORKER_CLIENT_ID \
  -e K6_PA_CASEWORKER_CLIENT_SECRET \
  -e K6_CONTENDER_CLIENT_ID \
  -e K6_CONTENDER_CLIENT_SECRET \
  -e K6_POLL_TIMEOUT_MS \
  -e K6_POLL_INTERVAL_MS \
  -e K6_SUMMARY_PATH \
  -e K6_CONTENTION_VUS \
  -e K6_PERFORMANCE_VUS \
  -e K6_PERFORMANCE_DURATION \
  -e K6_THRESHOLDS \
  -e K6_OAUTH_SCOPE \
  -v "$repo_dir:/work:ro" \
  -v "$repo_dir/$summary_dir:/work/$summary_dir" \
  -w /work \
  grafana/k6:0.57.0 run \
  -e "K6_API_BASE_URL=${K6_API_BASE_URL-}" \
  -e "K6_OAUTH_TOKEN_URL=${K6_OAUTH_TOKEN_URL-}" \
  -e "K6_SERVICE_NAME=${K6_SERVICE_NAME-}" \
  -e "K6_PROVIDER_CLIENT_ID=${K6_PROVIDER_CLIENT_ID-}" \
  -e "K6_PROVIDER_CLIENT_SECRET=${K6_PROVIDER_CLIENT_SECRET-}" \
  -e "K6_APPLICATION_CASEWORKER_CLIENT_ID=${K6_APPLICATION_CASEWORKER_CLIENT_ID-}" \
  -e "K6_APPLICATION_CASEWORKER_CLIENT_SECRET=${K6_APPLICATION_CASEWORKER_CLIENT_SECRET-}" \
  -e "K6_PA_CASEWORKER_CLIENT_ID=${K6_PA_CASEWORKER_CLIENT_ID-}" \
  -e "K6_PA_CASEWORKER_CLIENT_SECRET=${K6_PA_CASEWORKER_CLIENT_SECRET-}" \
  -e "K6_CONTENDER_CLIENT_ID=${K6_CONTENDER_CLIENT_ID-}" \
  -e "K6_CONTENDER_CLIENT_SECRET=${K6_CONTENDER_CLIENT_SECRET-}" \
  -e "K6_POLL_TIMEOUT_MS=${K6_POLL_TIMEOUT_MS-}" \
  -e "K6_POLL_INTERVAL_MS=${K6_POLL_INTERVAL_MS-}" \
  -e "K6_SUMMARY_PATH=$K6_SUMMARY_PATH" \
  -e "K6_CONTENTION_VUS=${K6_CONTENTION_VUS-}" \
  -e "K6_PERFORMANCE_VUS=${K6_PERFORMANCE_VUS-}" \
  -e "K6_PERFORMANCE_DURATION=${K6_PERFORMANCE_DURATION-}" \
  -e "K6_THRESHOLDS=${K6_THRESHOLDS-}" \
  "/work/k6/${scenario#"$pack_dir"/}" "$@"
