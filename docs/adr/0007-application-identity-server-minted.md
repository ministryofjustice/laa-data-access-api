# Application identity: server-minted in the target; client-supplied as an interim shim

The service should own application identity. The aggregate id (application id) should be minted by the service at draft-creation time and returned to the caller in the `Location` header — matching the pattern already used for prior-authority drafts.

Civil Apply are not yet ready to consume a server-minted id in their full integration. For now, they issue their own UUID and we set it on the draft as the aggregate id. This is an **explicit, temporary accommodation**, isolated to a single seam in the draft-creation service.

We chose this over: (a) forcing Civil Apply to adopt server ids immediately (would block integration) or (b) maintaining two genesis paths long-term (creates identity ambiguity).

## Consequences

- The client-supplied-id path is a **known temporary function to be removed** once Civil Apply adopt full draft integration. It must remain isolated so flipping to server-minted is a one-line change.
- Duplicate-id rejection without a projection read is an open question while the id is client-supplied. See OPEN-QUESTIONS.
- The draft and submission share one aggregate/stream; `POST /applications/{id}/submit` is a guarded transition on that same id.
