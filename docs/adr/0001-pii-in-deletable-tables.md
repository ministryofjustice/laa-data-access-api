# Store PII in deletable tables, not in the event stream

We need to retain the full history of an application's state changes while satisfying GDPR: personal data must be erasable on request. Storing PII in Axon's append-only event store makes erasure either impossible (rewriting immutable history) or requires crypto-shredding (keeping encrypted PII forever, adding key-management overhead).

We decided to use **pointer events**: all personal and security-sensitive data lives only in dedicated, deletable tables (`drafts`, `submissions`, `documents`) and in SDS. Events carry only ids and non-PII structural fields. GDPR erasure deletes those table rows and SDS objects; the event stream and all projections survive untouched.

See framework ADR [0001-pointer-events-for-gdpr](../laa-data-access-api-workbench/docs/adr/0001-pointer-events-for-gdpr.md) for the general pattern.

## Considered Options

- **Crypto-shredding** — rejected: keeps encrypted PII in the store indefinitely; adds per-subject key management; read models degrade silently after key deletion.
- **PII in deletable tables** (chosen) — clean erasure; event stream and projections survive; simpler operational model.

## Consequences

- The `drafts`, `submissions`, and `documents` tables are **write-side systems of record**, not replayable projections. They must not be wiped on a projection reset.
- Read models that need the PII body must join to these tables (by `contentId` / entity id) at query time — not derive it from events.
- The boundary is enforced by rule: no event field may carry PII. An ArchUnit rule should assert this mechanically.
