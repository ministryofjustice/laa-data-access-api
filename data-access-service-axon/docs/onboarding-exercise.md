# Axon Module Onboarding Exercise

This exercise turns the architecture documents into observable behaviour. Complete it after reading
[Axon in this module](axon-in-this-module.md). Avoid using real personal or case data.

## Learning outcomes

By the end, you should be able to explain:

- how a target aggregate ID selects an event stream;
- how an aggregate is rebuilt before command handling;
- the difference between event sequence, `applicationVersion`, and `applicationDataVersion`;
- how a thin event locates a complete sensitive-data version;
- why command completion and projection visibility are different moments;
- how projection replay differs from aggregate replay.

## 1. Run the service

Follow [Running and operating](running-and-operating.md) to start PostgreSQL and the Axon module.
Use the generated Swagger UI or a local API client to submit synthetic data only.

Use a fixed UUID for the application so it is easy to find in each table. The valid request builders
in `PostgresAxonIntegrationTest` are a useful reference for the required payload shape.

## 2. Create a standalone application

Submit `POST /api/v0/applications` without a lead application ID. Record:

- response status and `Location`;
- application UUID;
- whether the response was `201 Created` or `202 Accepted`.

If it is `202`, retrieve the `Location` after the projection catches up. Explain why this does not
mean application creation failed.

## 3. Inspect the creation records

Connect to PostgreSQL as described in the operational guide and inspect metadata without copying
sensitive payloads into notes:

```sql
SET search_path TO axon;

SELECT aggregate_identifier, sequence_number, payload_type, time_stamp
FROM domain_event_entry
WHERE aggregate_identifier = 'application-uuid-here'
ORDER BY sequence_number;

SELECT application_id, version, payload_hash, created_at
FROM application_data
WHERE application_id = 'application-uuid-here';

SELECT application_id, application_version, application_data_version, modified_at
FROM application_current_state
WHERE application_id = 'application-uuid-here';
```

Confirm that creation produced event sequence `0`, data version `0`, application version `0`, and a
current-state pointer to data version `0`.

## 4. Make a decision

Submit a valid decision using the current `applicationVersion`. Then repeat the inspection queries.

Expected changes:

- Axon event sequence advances by one;
- a new immutable `application_data` row is appended;
- `applicationVersion` advances;
- `applicationDataVersion` advances;
- the old data row still exists;
- the stored event is thin and does not contain the full decision request.

Do not display a real payload. With synthetic data, you may inspect the event JSON in an isolated
development database to verify that proceedings, certificate details, and free-text description are
not present.

## 5. Try a stale decision

Repeat a decision using the old `applicationVersion`.

Confirm:

- the API returns `409 Conflict`;
- no new event sequence appears;
- no new `application_data` version appears;
- the current-state row is unchanged.

Explain why both Axon's stream concurrency and the public application version exist.

## 6. Read current state and history

Call the application query and history endpoint. Identify which response fields came from:

- the thin current-state/history projection;
- the referenced `application_data` version;
- Axon event metadata such as service name.

Compare the response with [Storage model](storage-model.md).

## 7. Follow linking

Create a second synthetic application referencing the first as its lead. Inspect event metadata for:

- the associated application's `ApplicationCreatedEvent`;
- the lead application's `LinkedApplicationGroupRequested`;
- the deterministic linked-group stream;
- the group current-state projection;
- lead and member history rows.

Use the [linked creation sequence](sequence-diagrams/02-linked-application-creation.md) to explain why
a missing lead would reject the original request rather than fail later.

## 8. Observe replay in a test

Run the recovery tests:

```bash
./gradlew :data-access-service-axon:test \
  --tests uk.gov.justice.laa.dstew.access.EventProcessorRecoveryInMemoryTest
```

Read the reset/replay test while it runs. Identify:

- which processors are stopped and reset;
- which `@ResetHandler` clears each read model;
- how events repopulate projections;
- how token progress behaves for transient and permanent failures.

Do not experiment by manually deleting local token rows. Use the test's processor lifecycle APIs.

## 9. Add one aggregate test

As a final exercise, add or locally modify an aggregate fixture scenario:

```text
given ApplicationCreatedEvent
when a command violates one rule
then expect the domain exception and no events
```

Then change the given event state so the command succeeds and expect the exact thin event. This
demonstrates that command behaviour comes from replayed events, not test setup through private
fields.

## Review questions

You should be able to answer these before changing production command/event code:

1. Which method makes a business decision, and which method rebuilds aggregate state?
2. Why must an event-sourcing handler avoid repositories and clocks?
3. Why can `sendAndWait` return before a query projection is current?
4. Which data belongs in an event, and which belongs in `application_data`?
5. What would break if an event class were renamed after it had been stored?
6. Why is the linking router subscribing, and when would a saga be more suitable?
7. What remains reconstructable after retention deletes detailed payloads?

Use the [glossary](glossary.md) and [guardrails](guardrails.md) for any answers that remain unclear.
