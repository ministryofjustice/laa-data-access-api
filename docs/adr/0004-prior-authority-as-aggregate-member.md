# Model PriorAuthority as an @AggregateMember, not a separate aggregate

A prior authority is a post-submission request added by Manage to an already-submitted application. The textbook DDD move would be a separate `PriorAuthority` aggregate. We chose to model it as an Axon `@AggregateMember` on the `Application` aggregate instead.

The key invariant is: "the application must be submitted (and eventually granted) before a prior authority can be created." A separate aggregate cannot enforce this in-aggregate — it would need a saga or a projection read. As an `@AggregateMember`, the guard is trivial: check the `submitted` flag on the root during command handling.

The accepted trade-off is stream growth: every prior authority lifecycle event appends to the application's stream. Snapshotting mitigates this. The member approach is reversible — an `@AggregateMember` can be extracted into its own aggregate if a distinct bounded context genuinely warrants it later.

See framework ADR [0002-aggregate-member-forward-matching-instances](../laa-data-access-api-workbench/docs/adr/0002-aggregate-member-forward-matching-instances.md) for the `ForwardMatchingInstances` requirement when holding members in a collection.

## Consequences

- `@AggregateMember(eventForwardingMode = ForwardMatchingInstances.class)` is mandatory to avoid silent state corruption when multiple prior authorities exist on one application.
- Assign/unassign for prior authorities routes through the application aggregate (to the owning member).
- Snapshotting is required as the application stream grows with post-submission activity.
- The "GRANTED, not SUBMITTED" precondition is approximated as `submitted == true` in code until the grant/autogrant lifecycle is modelled. See OPEN-QUESTIONS.
