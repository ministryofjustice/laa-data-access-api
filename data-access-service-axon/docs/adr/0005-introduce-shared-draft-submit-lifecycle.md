# ADR 0005: Introduce a Shared Draft → Submit Lifecycle for Application-like Aggregates

- Status: Proposed
- Date: 2026-08-11
- Scope: `data-access-service-axon`
- Supersedes: ADR002 — *Store Draft Application Data Within the Application API Using Isolated
  Endpoints and TTL Policies* (see [Related documentation](#related-documentation))
- Conforms to: ADR004 — *Adopt a hybrid API style (REST resources + action endpoints)*

## Decision

Application-like aggregates — `Application` and `PriorAuthority` — share **one draft → submit
lifecycle** on a single identity:

1. **Lifecycle.** Status is `DRAFT → SUBMITTED`. A server-minted **genesis event** establishes the
   aggregate in `DRAFT`. `SUBMITTED` is **not terminal**: the aggregate stays long-lived and accretes
   later work through its own commands. Discard/withdraw (`DISCARDED`) is out of scope (companion ADR).

2. **Server-minted identity, in place.** The service mints the aggregate id at draft creation and
   returns it in the `Location` header. The draft and the submitted aggregate are the **same
   identity and stream**; submit is a guarded transition on that id — there is **no separate draft
   object and no cross-object promotion**.

3. **Endpoints.** Using the hybrid REST + action style of ADR004, and slash-path actions to match
   the wider Access API surface:

   | Purpose | Application | Prior Authority |
   |---|---|---|
   | Create draft — server mints id, returns `201 + Location` | `POST /api/v0/applications` | `POST /api/v0/applications/{applicationId}/prior-authorities` |
   | Overwrite/update draft body (idempotent) | `PUT /api/v0/applications/{id}` | `PUT /api/v0/applications/{applicationId}/prior-authorities/{priorAuthorityId}` |
   | Submit (action, no body) | `POST /api/v0/applications/{id}/submit` | `POST /api/v0/applications/{applicationId}/prior-authorities/{priorAuthorityId}/submit` |
   | Read | `GET /api/v0/applications/{id}` | `GET /api/v0/applications/{applicationId}/prior-authorities/{priorAuthorityId}` |

   Creation is a `POST` **without** an id that mints the identity server-side and returns it in
   `Location`; the draft body is then edited with idempotent `PUT …/{id}` calls until submit reads
   it, validates against the full schema, seals it, and dispatches the command. `PriorAuthority`
   already works this way; the current `Application` code is an interim shim that accepts a
   client-supplied id via `PUT …/{id}` upsert, and converges on `POST`-create with a server-minted
   id. Request/response conventions (error envelope, idempotency keys, `schemaVersion`,
   sync `201` / async `202`) follow ADR004 and are **not re-specified here**.

4. **Command and event, per aggregate.** Each aggregate gains a submit command and a **thin,
   PII-free submitted event** — a pointer only (identity, optimistic-lock version, sealed-body
   version pointer, status, timestamp), carrying no personal data, per ADR 0002. The genesis event
   likewise establishes `DRAFT` as a thin event. The **exact field list and Axon wiring for each
   event are specified in the developer guide / SLICES, not fixed in this ADR** — this ADR fixes the
   *shape and rules*, not the field-level contract.

5. **Guarded, idempotent transition.** Submit is rejected unless the aggregate is in `DRAFT`. Once
   `SUBMITTED`, a repeat submit is a safe no-op returning a stable response, not a duplicate event.

6. **Draft mutability and sealing.** Draft body edits are last-write-wins while in `DRAFT`. Submit
   is the point at which the body becomes immutable. Full-schema, submission-grade validation runs
   at submit; draft-stage validation is structural/relaxed (shape, not completeness).

The persistence/table model, discard semantics, and the GDPR erasure process are deferred to
companion ADRs (see [What this ADR does not decide](#what-this-adr-does-not-decide)).

## Context

Today the module creates an application in one shot: `ApplicationCreatedEvent` is genesis and the
`application_data` payload is immutable from version 1 — there is no draft window and no `/submit`.

The **Prior Authority (PA) slice** is next and makes an explicit draft → submit lifecycle
non-optional: evidence upload is proxied and returns a **server-minted document id that must be
written back into the body before submit**, so PA needs a durable, server-identified draft *before*
it is final; PA creation is gated on the owning application having reached the required decision
state; and PA drafts mint their own identity. PA therefore needs a draft genesis and a submit
transition regardless. Because the event store is append-only, defining the lifecycle **once, shared
with `Application`, before PA writes its first events** is far cheaper than a later retrofit or two
divergent models.

The full forcing argument and prior reasoning live in the design brief
*"Introduce a shared draft → submit lifecycle before we start Prior Authority"* (workbench,
2026-08-11) and are not reproduced here.

## Relationship to prior decisions

- **Supersedes ADR002.** ADR002 chose **generic, opaque, throwaway drafts** stored under separate
  `/drafts` endpoints with **no promotion path** — drafts and applications as distinct lifecycle
  objects, consumers re-submitting through the application endpoints, TTL expiry, and the
  "application-with-a-draft-status" option explicitly rejected. This ADR **reverses that** for the
  Access API's domain aggregates: the draft **is** the application, typed and server-identified, and
  **promotes in place** via a guarded submit. The reversal is driven by PA's forcing constraints
  (evidence must anchor to a durable, typed, server-identified draft that becomes the submitted
  aggregate) which the opaque, no-promotion model cannot satisfy. As this service is the Access API,
  ADR002's generic draft model is superseded, not merely scoped around.
- **Conforms to ADR004.** Submit is a first-class **action endpoint**, exactly the pattern ADR004
  prescribes (it names `:submit` among its example actions). This ADR adopts ADR004's action model
  and defers to it for error envelope, idempotency, concurrency/versioning and sync/async handling.
  One divergence: this ADR uses **slash-path actions** (`/submit`) to match the Access API surface
  and the vertical-slice specs, rather than ADR004's colon form (`:submit`); ADR004's examples
  should be aligned to slash paths.
- **Builds on ADR 0002.** Events remain PII-free pointers and personal data stays in deletable
  tables; this ADR does not change that principle.

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

- The PA slice gets the durable, typed draft genesis it needs to anchor evidence and mint identity.
- One lifecycle shared by both aggregates instead of two divergent models.
- Submission becomes an intentional, testable domain transition with an explicit sealing/validation
  point.
- A timestamped genesis event exists to anchor retention/SLA/audit behaviour.
- The Access API converges on a single draft model (this ADR) rather than the split ADR002 model.

### Negative

- Adds durable event types to an append-only store; contracts must be designed for long-term replay.
- Requires a companion decision on the draft/submitted persistence model, including any migration.
- Introduces a lifecycle state machine the aggregates and projections must enforce and test.
- Consumers currently building against ADR002's generic `/drafts` model must move to the
  per-aggregate lifecycle.
- Idempotency and racing submits must be explicitly handled and tested (per ADR004).
- `Application` temporarily runs its existing one-shot shape until its post-PA convergence lands
  (see Rollout).

## Alternatives considered

- **Generic, opaque `/drafts` with no promotion path (ADR002).** Rejected — cannot anchor PA
  evidence to a durable typed draft, forces a brittle re-submit/copy step, and splits one lifecycle
  across two objects. See *Supersedes ADR002* above.
- **Treat creation as submission (status quo for `Application`).** Rejected — conflates provisional
  and authoritative state and gives no mutable-draft window to stage evidence.
- **Add a `status` flag without a dedicated event.** Rejected — leaves no durable, replayable record
  of the transition and lets it happen without a guarded aggregate decision.
- **Model submission as a separate aggregate.** Rejected — submission is a transition in each
  aggregate's own single-stream lifecycle; splitting it adds no concurrency or ownership benefit.
- **Build a PA-only lifecycle and leave `Application` one-shot indefinitely.** Rejected — two
  divergent draft → submit models in one service; sharing the shape is cheaper over any horizon.

## Rollout (non-normative)

The lifecycle is **built in the PA slice first**. `Application`'s convergence onto the same shape is
**agreed in principle but sequenced after PA**: the existing one-shot `Application` create is not
changed in this slice, but no PA-only shape that `Application` cannot reuse is built. This sequencing
is a delivery decision and does not alter the architectural decision above.

## What this ADR does not decide

- The **draft/submitted persistence model** (table shape / mutability) — companion ADR. Note the
  open tension with ADR001 (JSON-blob → progressive relational rehydration) versus this module's
  immutable-versioned body model (ADR 0002); the companion ADR must reconcile them.
- **Discard / withdraw** (`DISCARDED`) semantics — companion ADR.
- The **GDPR erasure process** itself — separate, deliberate work.
- The **exact submitted/genesis event fields and Axon wiring** — developer guide / SLICES.
- The **timing of `Application`'s migration** onto the shared lifecycle — see Rollout.

## When to revisit

- when the draft/submitted persistence model is finalised (companion ADR);
- if submission must become asynchronous or await external validation (ADR004 `202` path);
- when discard/withdraw needs its own event and retention treatment;
- if a submitted aggregate must ever return to an editable state (currently disallowed);
- when `Application`'s convergence onto the shared lifecycle is scheduled.

## Acceptance criteria

Before changing this ADR to Accepted, agree:

- the exact submitted- and genesis-event fields for each aggregate, confirmed PII-free;
- the public API contract for the draft upsert and `POST …/submit`, including idempotent and
  illegal-transition responses, expressed in ADR004's conventions;
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

- ADR002 (superseded): *Store Draft Application Data Within the Application API Using Isolated
  Endpoints and TTL Policies* —
  https://dsdmoj.atlassian.net/wiki/spaces/WHCZA/pages/5835424028/
- ADR004 (conforms to): *Adopt a hybrid API style that uses REST resource endpoints alongside action
  endpoints for key application events* —
  https://dsdmoj.atlassian.net/wiki/spaces/WHCZA/pages/5918491057/
- [ADR 0002: Separate sensitive application data from events](0002-separate-sensitive-data-from-domain-events.md)
- [ADR 0003: Define application behaviour after retention deletion](0003-define-application-behaviour-after-retention-deletion.md)
- [Architecture overview](../architecture.md)
- [Events and sensitive data](../events-and-sensitive-data.md)
- [Event evolution](../event-evolution.md)
