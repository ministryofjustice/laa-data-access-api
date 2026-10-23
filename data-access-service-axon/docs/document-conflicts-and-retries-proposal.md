# Application and Prior Authority Document Conflicts and Retries

Status: Implemented for draft filename concurrency; other policy changes remain deferred.
Date: 2026-10-08

## Scope

This proposal covers document upload conflicts, retries, and failed-upload cleanup for Applications
and Prior Authority, plus Prior Authority-only document deletion and type-update concerns. It records
completed Application verification and remaining Prior Authority integration-test gaps.

DELETE readiness/idempotency, failed-upload compensation, and related OpenAPI changes remain deferred.
Draft rows use JPA optimistic locking. A concurrent stale update is retried once through the
existing command dispatcher. If that retry also fails, the existing generic `500 Internal Server
Error` response remains; no client version parameter or `409 Conflict` mapping is introduced.

The shared upload scope covers UUID-keyed document endpoints. Application document deletion and
type updates remain unimplemented; the
deletion and type-update proposals below do not apply to Applications. The legacy Application
upload endpoint keyed by original filename is outside this verification scope.

See [Document lifecycle](document-lifecycle.md) and
[Testing Axon code](testing-axon-code.md).

## Deletion readiness and repeated deletion

DELETE currently returns `204` without waiting for its projection, and repeated deletion
of an already-deleted document is rejected. The proposed behaviour is:

Reuse the subscription-await pattern, registering the subscription before dispatch.
The readiness query must confirm that the specific document is marked deleted, rather
than merely absent: absence can mean its upload has not yet been projected.

- Return `204` when deletion is projected.
- Return `202` when the bounded projection wait times out after command commit.
- Keep SDS cleanup best-effort; projection readiness does not prove physical removal.
- An already-started download may finish. Projection confirmation is not cancellation
  of in-flight reads or signed URLs.
- Repeated DELETE of a known deleted document succeeds idempotently, with the same
  readiness rules, without another deletion event or duplicate SDS cleanup.
- An unknown or wrong-owner document still returns `404`.

Implementation would need a dedicated query/update signal, use-case/controller changes,
and an OpenAPI `202` response. The interaction of repeated DELETE with subsequent
submission must be decided before implementation; existing draft-only rules still apply.

## Failed upload cleanup

Upload stores the file in SDS before registration. Failed registration can leave an orphan
because SDS is outside the database transaction.

If SDS storage succeeds but registration is confirmed rolled back after retries, attempt
best-effort removal of that upload's exact owner/document/suffix key. Preserve the
original error when cleanup fails and log sufficient non-sensitive identifiers for
recovery. Do not remove the object if the commit outcome is uncertain. A concrete,
reliable rollback classification is a prerequisite, not an assumption about all errors.

## Remaining verification

Extend the existing HTTP/PostgreSQL/Axon tests, keeping SDS mocked:

- Exercise competing type updates to the same document. Verify both accepted changes appear
  in event history and the last committed type becomes current.
- Force type-update and deletion transaction overlap with submission in both commit orders.
  If mutation commits first, submission must preserve its effect; if submission commits first,
  reject the mutation without recreating the draft or changing submitted version zero.
- Pause deletion projection and exercise GET during that interval, including filename removal
  and exact SDS suffix lookup. Do not assume immediate revocation under the current contract.
- Download content after a Prior Authority decision to verify projected-version hydration.
Use deterministic gates, bounded waits, and cleanup in `finally`. If a test exposes a production
defect, report it separately rather than implementing a deferred policy to make the test pass.

Add tests for deletion readiness, idempotent repeated deletion, and failed-upload compensation
only when those policy changes are authorised. They are not current guarantees.

## Application upload verification

`ApplicationDraftIntegrationTest` now covers the shared upload concerns through HTTP, PostgreSQL,
and Axon with SDS mocked:

- Two distinct uploads forced to read stale draft state retain both filename entries, document
  events, and downloadable content after one rolled-back/retried registration.
- Upload and submission transactions overlap in both commit orders. Upload-first submission
  retries and preserves the filename in immutable version zero. Submission-first registration
  retries against the submitted aggregate and returns `400` without recreating the draft,
  registering the document, or changing version zero.
- An upload paused in SDS while submission commits returns `400` on late registration. No
  document is registered and SDS cleanup is not attempted, demonstrating the existing orphan risk.
- A paused upload projection leaves document GET at `404` without calling SDS, despite upload
  returning `201`. Once projection resumes, GET returns content using the exact recorded suffix.
- Downloads work after a refused Application decision, with both the original filename and the
  missing-filename fallback, using the projected immutable data version.
- Two forced concurrency failures exhaust registration retries and return `500`. Neither
  registration attempt persists a filename or event; SDS upload runs once, without compensation.
- Concurrent uploads against stale draft reads retain both filenames after one optimistic-lock
  retry. If the bounded retry is exhausted, registration continues to return `500`.

### Submission-first registration outcome

When submission commits after the upload transaction has already read the draft, late registration
retries against the submitted aggregate and returns `400` from the existing lifecycle validation.
When submission finishes before registration starts, the upload preflight also rejects the request
with `400`. Both paths leave the submitted content unchanged and do not recreate the draft or
register the document. If an internal retry is exhausted by concurrent-write failures, the response
remains `500`; this persistence-only version does not introduce a client version parameter.