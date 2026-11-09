# Failure Behaviour

This reference describes expected outcomes for common command and projection failures. It helps
distinguish a rejected command from a successfully committed command whose projections are delayed.

| Scenario | HTTP result | Event committed? | Data version committed? | Expected action |
|---|---|---:|---:|---|
| Command targets a missing application aggregate | `404 Not Found` | No | No | Correct the application ID |
| Linked application references a missing lead/member | `404 Not Found` | No | No | Correct or create the referenced application |
| Linking violates a group invariant | `400 Bad Request` | No | No | Correct the group relationship |
| Decision uses stale `applicationVersion` | `409 Conflict` | No | No | Reload current state and retry with the current version |
| Request or schema validation fails | `400 Bad Request` | No | No | Correct the request |
| Application ID is retried with different creation data | `409 Conflict` | No new event | No new row | Use the original data or a new ID |
| Identical application creation is retried | `201` | No duplicate event | No duplicate row | Treat as an idempotent success |
| `application_data` append fails | Server error | No | No new row | Diagnose database failure; retry only when safe |
| Event persistence fails after data append begins | Server error | No | Rolled back with command transaction | Diagnose transaction/database failure |
| Tracking projection handler fails transiently | Write may already have succeeded | Yes | Yes | Repair dependency/restart; processor retries without skipping event |
| Tracking projection handler fails permanently | Writes continue unless separately restricted; reads may lag | Yes | Yes | Fix handler/data, then resume or reset processor |
| Referenced sensitive payload was retention-deleted | Depends on endpoint; hydration may be absent or degraded | Existing thin events remain | Referenced row absent | Follow the proposed lifecycle in ADR 0003; current behaviour is not a stable contract |
| Repeated unassignment when no caseworker is assigned | `200 OK` | No new event | No new row | Treat as idempotent success |

## Missing aggregate handling

Decision, assignment, and unassignment commands target an existing aggregate. Axon fails while
loading a missing event stream. `ApplicationExceptionHandler` converts the resulting
`AggregateNotFoundException`, including when found in the command failure chain by Spring's
exception resolution, into a stable application 404 response.

Creation and linking commands that use `CREATE_IF_MISSING` are different. They can receive a new
empty aggregate, so their handlers explicitly distinguish missing state and raise the appropriate
public exception.

## Command failure versus projection delay

A command failure means its transaction did not commit. A successful command response means the
event is durable. Command endpoints do not wait for tracking projections: single-resource reads
come from the sourced aggregate, so they reflect the write immediately. List and search reads come
from projections and may briefly lag.

```text
4xx/5xx from command handling → do not assume the write happened
201/204/200                   → command completed; the resource read reflects it
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
