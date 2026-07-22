# Extract WorkAllocation as its own aggregate, not held on Application

Caseworker assignment ("one active caseworker per work item") is an invariant. It was first modelled inside the `Application` aggregate — an `assignedCaseworkerId` field on the root and on each `PriorAuthority` member. This fuses allocation into the application's consistency boundary: every assign/unassign contends on the application stream, and independent assignment of two prior authorities on one application requires careful member routing.

We extracted allocation into a dedicated `WorkAllocation` aggregate, one instance per work item. Each instance's stream *is* the lock for that work item. Two prior authorities on one application are two separate streams with no cross-talk.

The `WorkAllocation` aggregate's natural key would be the work item id — but for application-level work items this collides with the `Application` stream (Axon keys by id, not by type). We namespace the allocation id: `UUIDv3("work-allocation:" + workItemId)`. See framework ADR [0003-namespace-stream-ids-for-same-identity-aggregates](../laa-data-access-api-workbench/docs/adr/0003-namespace-stream-ids-for-same-identity-aggregates.md).

## Considered Options

- **Allocation on Application** — simpler initially; creates stream contention; requires ForwardMatchingInstances config for per-member independence.
- **WorkAllocation aggregate** (chosen) — clean single-purpose boundary; independence by construction; no added DCB dependency.

## Consequences

- Assignment history lives on the allocation stream, not the application stream — an intentional split in a pointer-event model.
- Cross-item rules (e.g. "no caseworker holds more than N items") still require a saga or read-model check; the aggregate only enforces single-item mutual exclusion.
- The work item's existence check (read-model 404 guard) acts as the submitted/granted precondition gate — no in-aggregate guard needed.
