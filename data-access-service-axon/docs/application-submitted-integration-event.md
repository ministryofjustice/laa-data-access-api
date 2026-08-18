# ApplicationSubmitted Integration Event

Data Access publishes `ApplicationSubmitted` when an Application is created in
`APPLICATION_SUBMITTED`, or when an update changes its status from another value to
`APPLICATION_SUBMITTED`. Creation in progress and updates that remain submitted do not publish.

## Version 1 contract

The SNS message body is a thin trigger:

```json
{
  "eventType": "ApplicationSubmitted",
  "schemaVersion": 1,
  "eventId": "b6e6f0b2-0000-4000-8000-000000000001",
  "occurredAt": "2026-08-03T09:30:00Z",
  "source": "laa-data-access-api",
  "correlationId": "request-correlation-id",
  "data": {
    "applicationId": "8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f",
    "applyApplicationId": "8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f",
    "laaReference": null,
    "applicationVersion": 1
  }
}
```

`laaReference` is optional and currently omitted by the Axon producer to keep persisted internal
events free of business references. Consumers must tolerate it being null and ignore unknown
optional fields. Removing, renaming, or changing the meaning or type of an existing field requires
a new `schemaVersion`; additive optional data fields do not.

SNS attributes are:

- `eventType=ApplicationSubmitted`
- `applicationType=<APPLY|CCS|INITIAL>`

The event never contains Application content, proceedings, individuals, provider details, or other
personal data. Consumers fetch current authoritative state from Data Access using `applicationId`.

## Publication and delivery guarantee

`ApplicationSubmittedEventRouter` is a subscribing Axon event handler. For a qualifying live
command it registers an `AFTER_COMMIT` callback and calls SNS only after the event store and
immutable Application data commit. It is not an event-sourcing handler or tracking projection, so
aggregate reload and projection reset/replay cannot republish historical submissions.

Publication is intentionally best-effort and has no producer outbox or retry. An SNS failure is
logged with `eventId`, `applicationId`, and `correlationId`; it does not roll back the committed
Application. SNS-to-SQS delivery is at least once, so consumers must remain idempotent.

## Authentication and configuration

The AWS client follows the same mechanism as `laa-data-claims-event-service`: Spring Cloud AWS
4.0.2 with the AWS SDK default credentials provider. In Cloud Platform, the pod service account
provides IRSA/web-identity credentials. LocalStack uses the same chain with dummy environment
credentials and an endpoint override.

Required runtime settings are:

| Variable | Meaning |
|---|---|
| `AWS_REGION` | SNS region; defaults to `eu-west-2` locally |
| `APPLICATION_INTEGRATION_EVENTS_TOPIC_ARN` | ARN of the shared Data Access events topic; enables the publisher |
| `AWS_ENDPOINT_URL` | Optional LocalStack endpoint; absent in deployed environments |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Dummy LocalStack credentials only; not set for IRSA deployments |

The deployed topic ARN must come from the environment's Cloud Platform SNS Terraform output.
