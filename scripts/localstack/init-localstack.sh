#!/usr/bin/env bash
# LocalStack bootstrap for data-access-service-axon's producer side of the
# event-driven auto-grant SNS-to-SQS seam.
#
# Idempotent: safe to run again against an already-provisioned LocalStack instance.
set -euo pipefail

: "${AWS_REGION:=eu-west-2}"
: "${DATA_ACCESS_EVENTS_TOPIC_NAME:=data-access-events}"
: "${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME:=data-access-events-producer-smoke-test-queue}"

echo "Waiting for LocalStack SNS..."
until awslocal sns list-topics >/dev/null 2>&1; do
  echo "SNS is not ready yet"
  sleep 2
done

echo "Waiting for LocalStack SQS..."
until awslocal sqs list-queues >/dev/null 2>&1; do
  echo "SQS is not ready yet"
  sleep 2
done

echo "Creating SNS topic: ${DATA_ACCESS_EVENTS_TOPIC_NAME}"
TOPIC_ARN=$(awslocal sns create-topic --name "${DATA_ACCESS_EVENTS_TOPIC_NAME}" --query 'TopicArn' --output text)

echo "Creating producer-side verification queue: ${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}"
QUEUE_URL=$(awslocal sqs create-queue --queue-name "${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME}" --query 'QueueUrl' --output text)
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "${QUEUE_URL}" --attribute-name QueueArn --query 'Attributes.QueueArn' --output text)

echo "Subscribing ${DATA_ACCESS_EVENTS_SMOKE_TEST_QUEUE_NAME} to ${DATA_ACCESS_EVENTS_TOPIC_NAME} (filter: eventType=ApplicationSubmitted)"
SUBSCRIPTION_ATTRIBUTES_FILE=$(mktemp)
trap 'rm -f "${SUBSCRIPTION_ATTRIBUTES_FILE}"' EXIT
cat >"${SUBSCRIPTION_ATTRIBUTES_FILE}" <<JSON
{
  "FilterPolicy": "{\"eventType\":[\"ApplicationSubmitted\"]}",
  "FilterPolicyScope": "MessageBody"
}
JSON
awslocal sns subscribe \
  --topic-arn "${TOPIC_ARN}" \
  --protocol sqs \
  --notification-endpoint "${QUEUE_ARN}" \
  --attributes "file://${SUBSCRIPTION_ATTRIBUTES_FILE}"

echo "LocalStack producer seam ready."
echo "  Topic ARN: ${TOPIC_ARN}"
echo "  Verification queue URL: ${QUEUE_URL}"
