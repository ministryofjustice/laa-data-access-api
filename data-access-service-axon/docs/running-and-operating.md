# Running and Operating the Axon Module

This guide covers local development and basic diagnosis of the proof-of-concept module. It is not a
production operations runbook.

## Prerequisites

- Java 25, as required by the repository build.
- Docker for local PostgreSQL and Testcontainers integration tests.
- GitHub Packages credentials configured as described in the repository `README.md`.

Do not put a personal access token in the repository's `gradle.properties`. Use
`~/.gradle/gradle.properties` or the supported environment variables.

## Start locally

The repository has a dedicated Compose stack containing the Axon service, PostgreSQL, datasource
configuration, health checks, ports, and persistent volume. Start the complete stack from the
repository root:

```bash
docker-compose -f docker-compose.axon.yml up -d
```

Rebuild the service image after code or dependency changes:

```bash
docker-compose -f docker-compose.axon.yml up -d --build
```

Inspect status and logs with:

```bash
docker-compose -f docker-compose.axon.yml ps
docker-compose -f docker-compose.axon.yml logs -f data-access-service-axon
```

Stop the stack without deleting its PostgreSQL volume:

```bash
docker-compose -f docker-compose.axon.yml down
```

Defaults are:

| Setting | Value |
|---|---|
| HTTP port | `8082` |
| Database from the host | `laa_data_access_api` on `localhost:5433` |
| Database inside Compose | `postgres-axon:5432` |
| Database user | `laa_user` |
| PostgreSQL schema | `axon` |
| Axon Server | Disabled; events use the JPA/PostgreSQL store |
| Projection wait timeout | 5 seconds |

Flyway creates and validates the `axon` schema during startup. Swagger UI is available through the
module's Springdoc configuration at `http://localhost:8082/swagger-ui/index.html` once the service
is healthy.

To run the Axon service with its local SNS topic and verification queue, include LocalStack and
explicitly enable the optional publisher:

```bash
DATA_ACCESS_EVENTS_TOPIC_ARN=arn:aws:sns:eu-west-2:000000000000:data-access-events \
AWS_ENDPOINT_URL=http://localstack:4566 \
docker compose -f docker-compose.axon.yml -f docker-compose.localstack.yml up -d --build
```

The publisher remains disabled when no topic ARN is supplied, so the Axon-only Compose stack does
not depend on a LocalStack hostname. In UAT, where the Cloud Platform resources currently exist,
the chart reads the topic ARN from the `data-access-events-sns-topic` secret and runs as the
`laa-data-access-uat-irsa` service account. `AWS_ENDPOINT_URL` and static credentials remain unset,
so the AWS SDK default credentials provider uses web-identity credentials. Enable the same chart
switch in later environments only after their equivalent topic, secret, policy, and IRSA service
account have been provisioned.

Run the producer delivery test with Docker available:

```bash
./gradlew :data-access-service-axon:integrationTest \
  --tests '*ApplicationSubmittedLocalStackIntegrationTest'
```

An SNS failure after commit is logged as `Failed to publish ApplicationSubmitted event after
commit` with the event, Application, and correlation identifiers. This is an accepted missed-
trigger risk: the Application remains committed and later reconciliation must detect it.

The read-only reconciliation runs every five minutes by default and reports submitted Applications
whose `autoGrant` remains null after 15 minutes. Override these independently with
`ASSESSMENT_RECONCILIATION_INTERVAL` and `ASSESSMENT_RECONCILIATION_THRESHOLD`. It queries the
replayable Application projection through Axon's query gateway; it never publishes, assesses, or
updates an Application. Inspect:

- `application_assessment_stalled` and `application_assessment_oldest_age_seconds` for genuine
  unassessed-Application backlog;
- `application_assessment_reconciliation_fresh` to confirm those reconciliation gauges came from
  the latest scheduled query;
- `axon_event_processor_running`, `axon_event_processor_error`, and
  `axon_event_processor_caught_up` for projection health;
- `application_submitted_publication_total{outcome="success|failure"}` for direct SNS publication.

These metrics are exposed at `/actuator/prometheus`. Treat a lagging or failed
`application-projection` processor as a stale reconciliation source before investigating a
reported Application as a missed publication.

## Build and test

The normal repository build runs compilation, packaging, standard unit/in-memory tests, Checkstyle,
and Spotless checks across the subprojects:

```bash
./gradlew clean build
```

The Axon `integrationTest` source set is a custom task and is **not** included in Gradle's standard
`build` lifecycle. Run it separately to execute the PostgreSQL tests through Testcontainers:

```bash
./gradlew :data-access-service-axon:integrationTest
```

For a clean build plus all current Axon tests, run:

```bash
./gradlew clean build :data-access-service-axon:integrationTest
```

For a faster Axon-only standard build:

```bash
./gradlew :data-access-service-axon:clean :data-access-service-axon:build
```

Apply Java formatting with:

```bash
./gradlew :data-access-service-axon:spotlessApply
```

## Inspect the stores

Open a PostgreSQL shell in the local container:

```bash
docker-compose -f docker-compose.axon.yml exec postgres-axon \
  psql -U laa_user -d laa_data_access_api
```

Use read-only queries when diagnosing a running service:

```sql
SET search_path TO axon;

-- Event streams and their latest sequence numbers
SELECT aggregate_identifier, type, MAX(sequence_number) AS latest_sequence
FROM domain_event_entry
GROUP BY aggregate_identifier, type
ORDER BY aggregate_identifier;

-- Tracking processor ownership and token rows
SELECT processor_name, segment, owner, timestamp
FROM token_entry
ORDER BY processor_name, segment;

-- Thin current state and its sensitive-data pointer
SELECT application_id, application_version, application_data_version, modified_at
FROM application_current_state
ORDER BY modified_at DESC;

-- Available immutable data versions; avoid selecting payload in shared logs
SELECT application_id, version, payload_hash, created_at
FROM application_data
ORDER BY application_id, version;

-- Linked groups
SELECT group_id, lead_application_id, member_ids, modified_at
FROM linked_application_group_current_state;
```

Do not copy event payloads, `application_data.payload`, or history request payloads into tickets or
chat without following the project's handling rules for sensitive data.

## Tracking processor failure

A tracking processor that cannot handle an event should not advance its token past that event. This
preserves the failure for diagnosis instead of silently losing it.

1. Identify the failing processing group and event from logs.
2. Confirm whether the failure is transient, a data problem, or a handler defect.
3. Fix the cause before restarting or replaying the processor.
4. Confirm that its token advances and the affected projection catches up.

Do not manually edit `token_entry` while the service is running. The module currently has no
production administration endpoint for reset/replay. A controlled implementation should use
Axon's event-processor lifecycle and reset APIs: stop the processor, invoke its reset handler and
reset its tokens, then restart it. The in-memory recovery tests demonstrate the expected reset,
replay, and failure semantics.

## Projection reset and replay

Only reset a projection when its event handlers can replay every retained event version and any
required `application_data` rows remain available.

Resetting a projection should:

- stop only the selected tracking processor;
- invoke its `@ResetHandler`, which clears its read table;
- reset only that processor's tokens;
- replay from the event store;
- verify row counts and representative API responses before declaring recovery complete.

It must not delete `domain_event_entry`, `application_data`, or another processor's tokens. See
[Projections and replay](projections-and-replay.md) for the data flow.

## Retention deletion

`application_data` rejects normal updates, deletes, and truncation. The migration revokes public
execution of the retention function, so an authorised database role must be granted permission
explicitly before it can run:

```sql
SELECT axon.delete_application_data_for_retention('application-uuid-here'::uuid);
```

The result is the number of deleted versions. This operation is irreversible and intentionally
leaves thin events and projections behind. Before invoking it, confirm the expected post-retention
API and command behaviour described in [Events and sensitive data](events-and-sensitive-data.md).

The proof of concept does not yet provide a production retention workflow, authorisation model, or
post-deletion application state. Those must be designed before operational use. Do not treat the
direct SQL call as a complete application workflow; the proposed target behaviour is described in
[ADR 0003](adr/0003-define-application-behaviour-after-retention-deletion.md).
