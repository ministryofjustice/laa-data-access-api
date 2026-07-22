# Discard is a soft-delete; GDPR erasure is a separate process

Discarding a draft application might appear to be the natural moment to delete its PII (the application body, attached documents, SDS objects). We explicitly separated these two concerns.

**Discard** is a provider-facing soft-delete/archive. It removes a `DRAFT` application from the provider's active view. It does not purge PII and does not delete SDS documents. `ApplicationDraftDiscarded` carries no document ids and triggers no cleanup reaction.

**GDPR erasure** is a separate, deliberate process — designed later — that purges `drafts`/`submissions`/`documents` rows and SDS objects for a given application, leaving the event stream and read models intact.

We made this split because treating a routine UI action (tidying a draft list) as a GDPR purge over-scopes it: it would couple a common, low-stakes action to safety-critical document-deletion machinery and the reference-extraction logic needed to avoid deleting referenced documents. If that machinery fails or is wrong, the discard fails or causes data loss.

## Consequences

- Discarded drafts retain their PII in the deletable tables until the GDPR erasure process runs.
- Orphaned SDS documents from abandoned or discarded drafts are acceptable until the GDPR process sweeps them.
- The GDPR deletion process (trigger, scope, audit trail, relationship to the event log) is a deferred design decision.
