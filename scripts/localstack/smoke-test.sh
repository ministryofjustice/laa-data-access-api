#!/usr/bin/env bash
# Producer-side smoke test for the local SNS-to-SQS seam.
#
# Publishes a version 1 ApplicationSubmitted envelope (with its agreed SNS message
# attributes) to the local data-access-events topic and verifies the private
# verification queue (see init-localstack.sh) receives the SNS-wrapped message.
#
# Usage:
#   scripts/localstack/smoke-test.sh
set -euo pipefail

: "${AWS_REGION:=eu-west-2}"
: "${AWS_ENDPOINT_URL:=http://localhost:4566}"
: "${DATA_ACCESS_EVENTS_TOPIC_NAME:=data-access-events}"
: "${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME:=data-access-events-producer-smoke-test-queue}"

awslocal() {
  aws --endpoint-url "${AWS_ENDPOINT_URL}" --region "${AWS_REGION}" "$@"
}

TOPIC_ARN=$(awslocal sns list-topics --query "Topics[?ends_with(TopicArn, ':${DATA_ACCESS_EVENTS_TOPIC_NAME}')].TopicArn | [0]" --output text)
QUEUE_URL=$(awslocal sqs get-queue-url --queue-name "${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}" --query 'QueueUrl' --output text)

if [ -z "${TOPIC_ARN}" ] || [ "${TOPIC_ARN}" = "None" ]; then
  echo "Topic ${DATA_ACCESS_EVENTS_TOPIC_NAME} not found; run scripts/localstack/init-localstack.sh first" >&2
  exit 1
fi

event_id="$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')"
application_id="$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')"
occurred_at="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"

message_body=$(cat <<JSON
{
  "eventType": "ApplicationSubmitted",
  "schemaVersion": 1,
  "eventId": "${event_id}",
  "occurredAt": "${occurred_at}",
  "source": "laa-data-access-api",
  "correlationId": "smoke-test-${event_id}",
  "data": {
    "applicationId": "${application_id}",
    "applyApplicationId": "smoke-test-apply-id",
    "laaReference": "SMOKE-TEST",
    "applicationVersion": 1
  }
}
JSON
)

echo "Publishing ApplicationSubmitted (eventId=${event_id}) to ${TOPIC_ARN}"
awslocal sns publish \
  --topic-arn "${TOPIC_ARN}" \
  --message "${message_body}" \
  --message-attributes '{"eventType":{"DataType":"String","StringValue":"ApplicationSubmitted"},"applicationType":{"DataType":"String","StringValue":"APPLY"}}' \
  >/dev/null

echo "Receiving from ${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}..."
received=""
for _ in $(seq 1 10); do
  received=$(awslocal sqs receive-message --queue-url "${QUEUE_URL}" --wait-time-seconds 1 --max-number-of-messages 10 --query 'Messages' --output json 2>/dev/null || echo "null")
  if [ "${received}" != "null" ] && printf '%s' "${received}" | grep -q "${event_id}"; then
    break
  fi
  sleep 1
done

if [ -z "${received}" ] || [ "${received}" = "null" ] || ! printf '%s' "${received}" | grep -q "${event_id}"; then
  echo "FAILED: did not observe eventId=${event_id} on ${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}" >&2
  exit 1
fi

echo "PASS: SNS-wrapped ApplicationSubmitted event ${event_id} received on ${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}"

# Clean up the message(s) we just consumed so repeated smoke-test runs stay tidy.
receipt_handles=$(printf '%s' "${received}" | python3 -c 'import json,sys; [print(m["ReceiptHandle"]) for m in json.load(sys.stdin)]')
while IFS= read -r handle; do
  [ -z "${handle}" ] && continue
  awslocal sqs delete-message --queue-url "${QUEUE_URL}" --receipt-handle "${handle}" >/dev/null
done <<<"${receipt_handles}"
