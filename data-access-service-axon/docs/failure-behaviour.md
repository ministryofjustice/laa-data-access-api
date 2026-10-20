# Failure Behaviour

This reference describes expected outcomes for common command and projection failures. It helps
distinguish a rejected command from a successfully committed command whose projection is delayed.

| Scenario | HTTP result | Event committed? | Data version committed? | Expected action |
|---|---|---:|---:|---|
| Command targets a missing application aggregate | `404 Not Found` | No | No | Correct the application ID |
| Linked application references a missing lead/member | `404 Not Found` | No | No | Correct or create the referenced application |
| Linking violates a group invariant | `400 Bad Request` | No | No | Correct the group relationship |
| Decision uses stale `applicationVersion` | `409 Conflict` | No | No | Reload current state and retry with the current version |
| Request or schema validation fails | `400 Bad Request` | No | No | Correct the request |
| Application ID is retried with different creation data | `409 Conflict` | No new event | No new row | Use the original data or a new ID |
| Identical application creation is retried | `201` or `202` | No duplicate event | No duplicate row | Treat as an idempotent success |
| `application_data` append fails | Server error | No | No new row | Diagnose database failure; retry only when safe |
| Event persistence fails after data append begins | Server error | No | Rolled back with command transaction | Diagnose transaction/database failure |
| Projection is not readable before timeout | `202 Accepted` with `Location` | Yes | Yes, if command changed data | Poll/read later; investigate only if lag persists |
| Tracking projection handler fails transiently | Write may already have succeeded | Yes | Yes | Repair dependency/restart; processor retries without skipping event |
| Tracking projection handler fails permanently | Writes continue unless separately restricted; reads may lag | Yes | Yes | Fix handler/data, then resume or reset processor |
| Referenced sensitive payload was retention-deleted | Depends on endpoint; hydration may be absent or degraded | Existing thin events remain | Referenced row absent | Follow the proposed lifecycle in ADR 0003; current behaviour is not a stable contract |
| Repeated unassignment when no caseworker is assigned | `200 OK` | No new event | No new row | Treat as idempotent success |

## Missing aggregate handling

Most decision, assignment, and unassignment commands target an existing aggregate via a bare
`@InjectEntity` parameter. Axon fails while loading a missing event stream, before the handler body
runs. `ApplicationExceptionHandler` converts the resulting
`EntityMissingForInstanceCommandHandlerException` into a stable application 404 response.

Creation and linking commands, and a small number of existing-aggregate commands with a
domain-specific missing-resource contract (for example note creation, application update, and
prior-authority grant validation), inject `ManagedEntity<ID, Aggregate>` instead. Its `entity()` is
`null` when the stream does not exist, so the handler runs and can explicitly raise
`ResourceNotFoundException` or distinguish new/existing state as required.


## Command failure versus projection delay

A command failure means its transaction did not commit. A projection timeout does not mean the
command failed: the event is durable, but a tracking processor did not make the read model visible
within five seconds.

```text
4xx/5xx from command handling → do not assume the write happened
202 with Location            → write happened; read model is still catching up
201/204/200                   → command completed with the endpoint's normal synchronous result
```

Clients should not blindly retry non-idempotent commands after an ambiguous network failure. They
should first retrieve current state and use the optimistic-lock version where the API provides one.

## Tracking processor recovery

Tracking processors retain their position in `token_entry`. A handler failure must not advance the
token beyond the failing event. After the cause is corrected, restart or reset the affected
processor through Axon's lifecycle APIs and verify its projection.

See [Running and operating](running-and-operating.md) for diagnosis and
[Projections and replay](projections-and-replay.md) for reset semantics.

The intended distinction between legitimate retention and corrupt missing data is proposed in
[ADR 0003](adr/0003-define-application-behaviour-after-retention-deletion.md).
