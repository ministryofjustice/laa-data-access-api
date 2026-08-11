# ADR 0005: Introduce a Shared Draft → Submit Lifecycle for Application-like Aggregates

- Status: Proposed
- Date: 2026-08-11
- Scope: `data-access-service-axon`

## Context

The module today creates an application in a single shot: `ApplicationCreatedEvent` is the genesis
event and the payload written to `application_data` is immutable from version 1. There is no draft
state, no `/submit` action, and therefore no point at which provisional, in-progress work is sealed
into an authoritative submission.

The **Prior Authority (PA) slice is next**, and it is the point at which an explicit draft → submit
lifecycle stops being optional. Three properties of the PA work force it:

1. **Evidence staging requires a durable, server-identified draft.** PA carries documents, and
   document upload is proxied through this service (direct client → SDS upload is not permitted). An
   upload returns a **server-minted document id that must be written back into the body before
   submit**. To attach evidence you therefore need a durable draft, identified by the service,
   *before* the PA is final. This is a forcing constraint, not a stylistic preference.
2. **PA creation is gated on a prior state.** A PA may only be created once the owning application
   has reached the required decision state. Enforcing that needs an explicit, queryable
   submitted/decided transition rather than an implicit one.
3. **PA drafts naturally mint their own identity.** The service will generate the PA identifier at
   draft creation. That is exactly the draft-genesis pattern this ADR generalises.

Building PA therefore requires a draft genesis and a submit transition regardless of this ADR. The
only real question is whether we build that shape **once, shared with `Application`**, or build a
PA-only variant now and diverge. Because the event store is append-only, retrofitting the lifecycle
onto a stream after it has accrued events is materially more expensive than defining it up front.

This ADR builds on the module's existing principle that events are PII-free pointers and personal
data lives in deletable tables (ADR 0002); it does not change it.

## Decision drivers

- The PA slice cannot be built well without a draft genesis and a submit transition (points 1–2).
- Avoid two divergent models of "draft → submit" for application-like aggregates in one service.
- Introduce the shape before PA writes its first events, while it is cheap.
- Give application-like aggregates a **server-minted identity** at draft creation, so identity never
  depends on client-supplied data and cannot collide.
- Create a single, explicit point at which the body is **sealed** and **full-schema** validation
  applies, distinct from the relaxed validation acceptable for a draft.
- Provide a **known, timestamped "draft created" moment** to anchor time-based behaviour (retention
  windows, SLAs, audit) — a genesis event, not an inferred timestamp.
- Keep personal/security data out of the event stream (ADR 0002).

## Decision

Adopt **one shared draft → submit lifecycle for application-like aggregates** — `Application` and
`PriorAuthority` — and realise it **in the PA slice first**.

1. **Lifecycle.** Model status as `DRAFT → SUBMITTED`. `SUBMITTED` is not terminal; the aggregate
   remains long-lived and accretes later work through its own commands. The genesis event
   establishes the aggregate in `DRAFT`. Discard/withdraw (a `DISCARDED` state) is **out of scope**
   and deferred to a companion ADR.

2. **Server-minted identity.** The aggregate identity is minted by the service at draft creation and
   returned to the caller (in the `Location` header). The draft and the submitted aggregate share
   **one identity/stream**; submit is a guarded transition on that same id.

3. **Command and event, per aggregate.** Add a submit command and a submitted event for each
   aggregate:
   - `Application`: `SubmitApplicationCommand` → `ApplicationSubmittedEvent`, a PII-free pointer
     using the module's existing vocabulary — `applicationId`
     (`@EventTag(key = "ApplicationAggregate")`), `applicationVersion` (optimistic-lock, as on
     `ApplicationDecisionMadeEvent`), `applicationDataVersion` (pointer to the sealed body version),
     `status`, and `occurredAt`. It carries no personal data.
   - `PriorAuthority`: an analogous `SubmitPriorAuthorityCommand` → `PriorAuthoritySubmittedEvent`,
     keyed on `priorAuthorityId`, following the same thin-pointer rules.
   Note that `ApplicationState` currently folds no `status`/lifecycle field, so this transition also
   introduces explicit lifecycle state into the aggregate's sourced state.

4. **Endpoint.** Expose the transition as a `POST /{...}/{id}/submit` action carrying no body.
   Submit reads the current draft body, validates and parses it, seals it, then dispatches the
   command.

5. **Guarded transition.** The aggregate rejects submit unless it is in `DRAFT`. Submit is
   idempotent once `SUBMITTED`; a second submit is a safe no-op / stable response, not a duplicate
   event.

6. **Draft mutability and sealing.** Draft body edits are last-write-wins while in `DRAFT`. Submit
   is the point at which the body becomes immutable. The concrete persistence shape (separate
   mutable and immutable tables, or an evolution of the append-only `application_data` model) is the
   subject of a companion decision; this ADR fixes the **lifecycle and events**, not the table model.

7. **Validation placement.** Draft-stage validation is structural/relaxed (shape, not completeness).
   Full-schema, submission-grade validation runs at submit, before the body is sealed and the event
   applied.

**Sequencing.** The lifecycle is **built in the PA slice**. `Application`'s convergence onto the
same shape is **agreed in principle but sequenced after PA**: we do not change the existing one-shot
`Application` create in this slice, but we do not build a PA-only lifecycle that `Application` cannot
reuse.

## Proposed command and query behaviour

| Behaviour | Result |
|---|---|
| Submit while `DRAFT` and valid | Seals the body, applies the submitted event, moves to `SUBMITTED` |
| Submit while `DRAFT` but invalid against the full schema | Rejected with a stable validation response; no event, body not sealed |
| Submit while already `SUBMITTED` | Idempotent no-op / stable response; no duplicate event |
| Post-submission commands (decision, assignment, note, prior authority, …) | Permitted only once `SUBMITTED`, per each command's own rules |
| Draft body edit while `DRAFT` | Overwrites the draft body (last-write-wins) |
| Draft body edit while `SUBMITTED` | Rejected; submitted bodies are immutable |

## Consequences

### Positive

- The PA slice gets the draft genesis it needs to anchor evidence and mint identity.
- One lifecycle, shared by both aggregates, instead of two divergent models to maintain.
- Submission becomes an intentional, testable domain transition with an explicit sealing/validation
  point.
- A timestamped genesis event exists to anchor retention/SLA/audit behaviour.
- No change to the ADR 0002 principle that events stay PII-free and bodies live in deletable tables.

### Negative

- Adds durable event types to an append-only store; the contracts must be designed for long-term
  replay (see [Event evolution](../event-evolution.md)).
- Requires a companion decision on the draft/submitted persistence model, including any migration.
- Introduces a lifecycle state machine the aggregates and projections must enforce and test.
- `Application` and `PriorAuthority` gain new public actions and lifecycle semantics consumers must
  understand.
- Idempotency and concurrency (racing submits) must be explicitly handled and tested.
- `Application` temporarily runs a different (one-shot) shape until its post-PA convergence lands.

## Alternatives considered

### Build a PA-only lifecycle now; leave `Application` one-shot indefinitely

Rejected. It creates two different models of "draft → submit" in one service, duplicates code and
tests, complicates the shared history/projection story, and is a lasting onboarding trap. Sharing
the shape is cheaper over any reasonable horizon.

### Treat creation as submission (status quo for `Application`)

Rejected. It conflates provisional and authoritative state, provides no mutable-draft window, gives
downstream work no reliable "is this real yet?" point, and cannot anchor evidence for PA (point 1).

### Add a `status` field/flag without a dedicated event

Rejected. A mutated flag creates no durable, replayable record of the transition, weakens audit
reconstruction, and lets the transition happen without a guarded aggregate decision.

### Model submission as a separate aggregate

Rejected. Submission is a transition in each aggregate's *own* lifecycle and its invariant ("cannot
submit unless in draft") is owned by that aggregate. A separate aggregate would split a single-stream
invariant across boundaries without a concurrency or ownership reason.

### Convert `Application` in the same slice

Rejected for scope. Converting the populated `Application` path is real work with its own migration
questions; forcing it into the PA slice would slow PA delivery. Agreeing the shared shape now and
sequencing `Application` after PA captures the benefit without the delay.

## What this ADR does not decide

- The **draft/submitted persistence model** (table shape / mutability) — companion ADR.
- **Discard / withdraw** (`DISCARDED`) semantics — companion ADR.
- The **GDPR erasure process** itself — separate, deliberate work.
- The **timing of `Application`'s migration** onto the shared lifecycle — agreed in principle,
  sequenced after PA.

## When to revisit

- when the draft/submitted persistence model is finalised (companion ADR);
- if submission must become asynchronous or await external validation;
- when discard/withdraw needs its own event and retention treatment;
- if a submitted aggregate must ever return to an editable state (currently disallowed);
- when `Application`'s convergence onto the shared lifecycle is scheduled.

## Acceptance criteria

Before changing this ADR to Accepted, agree:

- the exact submitted-event fields for each aggregate, confirmed PII-free;
- the public API contract for `POST /{...}/{id}/submit`, including idempotent and illegal-transition
  responses;
- the draft/submitted persistence model this depends on (companion decision);
- the full-schema validation applied at submit and the relaxed schema permitted for drafts;
- the prior-state gate for PA creation and how it is checked.

The implementation must then demonstrate, for the PA slice:

- a guarded `DRAFT → SUBMITTED` transition rejecting submission from any other state;
- idempotent re-submission and race handling;
- aggregate replay reconstructing the submitted state from thin events;
- projection reset/replay reproducing submitted current-state and history;
- no personal data in the submitted events, projections, logs, or API responses.

## Related documentation

- [ADR 0002: Separate sensitive application data from events](0002-separate-sensitive-data-from-domain-events.md)
- [ADR 0003: Define application behaviour after retention deletion](0003-define-application-behaviour-after-retention-deletion.md)
- [Architecture overview](../architecture.md)
- [Events and sensitive data](../events-and-sensitive-data.md)
- [Event evolution](../event-evolution.md)
