# LAA Data Access API — Domain Vocabulary

Domain language specific to the Legal Aid Agency Data Access API. Framework-level terms (aggregate, command, event, projection) are defined in the [framework vocabulary](../laa-data-access-api-workbench/CONTEXT.md).

## Core Domain

**Application**:
A legal aid application — the primary aggregate. Begins as a draft created by a provider and progresses through a defined lifecycle. Not to be confused with the software application itself.
_Avoid_: Claim (a billing concept), Case (used downstream)

**Draft**:
An application in the `DRAFT` status. The application body is mutable: overwritten in place (last-write-wins) until the application is submitted or discarded.

**Submission**:
The sealed, immutable body of an application written at the moment of submit. Write-once; the only permitted change is deletion (GDPR erasure).
_Avoid_: Application body, content

**ContentId**:
The UUID minted at submission time that identifies an immutable row in the `submissions` table. Carried as a pointer in `ApplicationSubmittedEvent`.

**PriorAuthority**:
A post-submission request — added by Manage to an already-submitted application — for prior approval of a specific action. Modelled as an `@AggregateMember` on the `Application` aggregate with its own `DRAFT → SUBMITTED` lifecycle.
_Avoid_: PA (acceptable abbreviation in code only)

**WorkItem**:
An entry in the `work_items` read model representing an assignable unit of caseworker work. Currently either an application or a prior authority. A projection row, not an aggregate.

**WorkAllocation**:
A dedicated event-sourced aggregate that owns the "one active caseworker per work item" invariant for a single work item. Keyed by a namespaced UUID derived from the work item id to avoid stream-id collision with the `Application` aggregate.

**Workload**:
The paginated read model of `work_items` queried by caseworker services (Decide). A projection; not an aggregate.
_Avoid_: Queue, task list

**Caseworker**:
A LAA staff member who reviews and makes decisions on work items. Identified by `caseworkerId` in assignment commands.

**Discard**:
A provider-facing soft-delete/archive of a `DRAFT` application. Removes the draft from the provider's active view. Does *not* purge PII or delete SDS documents — that is the GDPR deletion process.
_Avoid_: Delete, purge, cancel

## Actors / External Systems

**apply**:
The Civil Legal Aid application system. The primary command source for draft creation, updates, submission, and document attachment.

**Manage**:
The post-submission case management service. Adds prior authorities and other post-submission items to an already-submitted application.

**Decide**:
The caseworker service. Reads workload queues and issues assign/unassign commands.

**SDS (Secure Document Storage)**:
The adjacent document store. Documents are proxied through this service on behalf of clients — direct client-to-SDS upload is not permitted.

## Key Identifiers

**applyApplicationId**:
The UUID issued by apply for an application during the interim identity shim period. Stored on the aggregate and in the `drafts` table. Will be replaced by a server-minted id once Civil Apply adopts full draft integration.

**DocumentId**:
An opaque UUID minted by this service and used as the SDS object key. The only document identifier stored in the aggregate and events; the original filename is kept only in the deletable `documents` table.
_Avoid_: SDS key, filename

**laaReference**:
The LAA-assigned reference for a submitted application. A non-PII structural field carried in `ApplicationSubmittedEvent`.

## GDPR / Privacy

**Pointer event**:
An event that carries only ids and non-PII structural fields, with the personal data body written sideways into a deletable table. The pattern used for all lifecycle events in this service.
_Avoid_: Lean event (correct in the framework context, but "pointer event" is the term used here)

**GDPR erasure**:
The deliberate process of purging PII rows (`drafts`, `submissions`, `documents`) and SDS objects for a given application, leaving the event stream and read models intact.
_Avoid_: Discard, delete (those are separate concerns)

**PII (Personally Identifiable Information)**:
Personal or security-sensitive data about applicants, providers, or individuals. Must never enter the event stream or any rebuildable projection.
