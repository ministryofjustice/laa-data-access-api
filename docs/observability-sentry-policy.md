# Sentry error reporting policy

This document defines the agreed rule for what gets reported to Sentry from
`data-access-service-axon`, and the mechanisms that enforce it.

## Background

The service sends errors to Sentry through two routes, configured via
`sentry-logback` and `sentry-spring-boot` (see `application.yml`, the Helm
`_envs.tpl` template, and `build.gradle` for the exact wiring):

- **Logs**: `sentry-logback` turns any `ERROR`-level log line into a Sentry
  event (`minimum-event-level: error`). Recent `INFO` logs are attached as
  breadcrumbs.
- **Unhandled exceptions**: the Spring Boot integration reports these
  automatically (exceptions that propagate out of a Spring MVC request with
  no matching `@ExceptionHandler`).

Before this policy, there was no agreed rule for what should be reported, so
the code was inconsistent: some exception handlers logged nothing, some
logged `ERROR` for routine client errors (4xx), a blanket `@AfterThrowing`
aspect logged every exception thrown from an annotated controller method at
`ERROR` regardless of cause, and background Axon projection failures were
swallowed with no Sentry event at all because they never reached either
route. The practical effect was simultaneous Sentry noise (expected,
client-caused failures) and Sentry blind spots (genuine, operator-actionable
failures that were silently dropped).

## The agreed rule

| Outcome | Example | Reported via | Sentry |
|---|---|---|---|
| Client-caused, expected (HTTP 4xx) | validation failure, version/stale conflict, not found, virus detected in upload, malformed JSON body | `log.warn(...)` | breadcrumb only, no event |
| Unexpected / operator-actionable (HTTP 5xx, background job failure, projection failure) | downstream service failure, data-integrity violation, scheduled job failure, event-processor exception | `ErrorReporter.report(...)` | event, via the log route (`log.error` internally) |
| Normal operational flow | method entry/exit, successful completion | `log.info(...)` | breadcrumb only |

**Principle: report once, at the boundary.** An exception should be reported
at its true severity exactly once, as close as possible to where it is
converted into an HTTP response (or, for non-HTTP paths such as scheduled
jobs and event-handler projections, at the point where it is irrecoverably
given up on or handed back to the framework). `ErrorReporter` makes "report
once" a property of the code rather than a convention every author has to
remember: every call to `ErrorReporter.report(...)` is safe to make
defensively, because a given throwable instance is only ever turned into a
Sentry event once, however many boundaries it passes through.

## How this is enforced

### 1. `ErrorReporter` is the single reporting boundary

`uk.gov.justice.laa.dstew.access.observability.ErrorReporter` is the only
class that should call `log.error(...)` for a caught exception. Its
`report(String message, Throwable throwable)` method:

- logs the throwable at `ERROR` (picked up by `sentry-logback` as a Sentry
  event), unless
- the throwable has already been reported — tracked by suppressing a cheap,
  stack-trace-free marker exception onto it (`Throwable#addSuppressed`),
  checked by reference equality on `Throwable#getSuppressed()`.

This means an inner boundary (e.g. a `*DataStore` read) can report a failure
for its own diagnostic context and rethrow it, and an outer boundary (e.g.
the generic `RuntimeException` backstop handler) can also call
`errorReporter.report(...)` defensively on the same instance without
producing a duplicate Sentry event.

Enforced by `AxonArchitectureTest#onlyErrorReporterMayLogAtErrorLevel`: no
class other than `ErrorReporter` and `LoggingAspects` (see §4) may call
`Logger.error(...)` anywhere in `data-access-service-axon`.

### 2. `ApplicationExceptionHandler` is the primary HTTP reporting boundary

Every `@ExceptionHandler` method in
`uk.gov.justice.laa.dstew.access.controller.ApplicationExceptionHandler`
explicitly chooses its severity before returning its response:

- 4xx handlers call `log.warn(...)`.
- 5xx handlers call `errorReporter.report(...)`.
- `handleHttpMessageNotReadableException` (malformed JSON, 400) deliberately
  does neither — it is an expected, high-volume client error that would be
  noisy even as a breadcrumb.
- A generic `handleUnexpectedException(RuntimeException)` backstop reports
  and returns a safe 500 for any unmapped unchecked exception, deliberately
  scoped to `RuntimeException` (not `Exception`) so Spring MVC's own checked
  `ServletException`-based request-binding failures still fall through to
  Spring's default (correct) 400 handling.

Severity is chosen per-handler, not inferred, so it cannot silently drift as
handlers are added or changed. This is enforced by
`ApplicationExceptionHandlerLoggingArchTest`, which fails the build if any
`@ExceptionHandler` method in that class does not call `log.warn(...)`,
`log.error(...)`, or `ErrorReporter.report(...)` (two narrowly named
handlers are exempt — see the test's Javadoc for why).

### 3. `*DataStore` classes report JSON deserialization/repository failures

Any class reading a JSON payload column (e.g. `@JdbcTypeCode(SqlTypes.JSON)`)
must depend on `ErrorReporter` and report repository/deserialization
failures before rethrowing, so a caller further up the stack (an HTTP
handler, a projection) doesn't have to know this is even possible —
see `ApplicationDataStore`/`PriorAuthorityDataStore` for the reference
pattern. Enforced for every `*DataStore` class in `..command..data..`
packages by `AxonArchitectureTest#dataStoreClassesMustDependOnErrorReporter`.

### 4. `LoggingAspects` classifies by default, for methods not using the handler

`uk.gov.justice.laa.dstew.access.shared.logging.aspects.LoggingAspects`
(in `data-access-shared`) provides `@LogMethodArguments` /
`@LogMethodResponse` aspect-based logging for ~40 annotated controller
methods. Its `@AfterThrowing` advice (`logMethodExceptionAdvice`) previously
logged every exception at `ERROR` unconditionally — the single biggest
source of Sentry noise. It now classifies by exception type:

- `WARN` (no Sentry event) for: `ClientFacingException` implementations,
  `IllegalArgumentException` (used throughout this codebase exclusively for
  bad caller input), Spring Security's `AccessDeniedException`, Spring's
  `MethodArgumentNotValidException`, and a small set of third-party
  framework exception types matched by fully-qualified class name (Axon's
  `EntityNotFoundException` / `EntityMissingForInstanceCommandHandlerException`
  — matched by name, not `instanceof`, because `data-access-shared` does not
  have a compile-time dependency on axon-framework).
- `ERROR` (Sentry event, via `sentry-logback` directly) for everything else.

`LoggingAspects` is a documented exception to "only `ErrorReporter` may call
`Logger.error`" (rule §1): it is a generic cross-cutting aspect in a module
that cannot depend on `data-access-service-axon`'s `ErrorReporter`, so its
own severity classification is the equivalent mechanism for the call sites
it covers. This is covered by `LoggingAspectsTest` in
`data-access-shared/src/test/java/.../shared/logging/aspects`.

### 5. The `ClientFacingException` marker interface

Exceptions that represent an expected, client-caused failure implement
`uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException`
(defined in `data-access-shared`). This is a marker interface with no
methods; it exists purely to classify exceptions for `LoggingAspects`.

Implementations include `ValidationException`, `ResourceNotFoundException`,
`FileConflictException`, `FileLengthRequiredException`,
`VirusDetectedException`, and the various `*ConflictException` /
`InvalidApplicationStateException` types used across application, prior
authority, work-item, and linked-group handling.

### 6. Background/event-driven paths without an HTTP caller

- **Axon pooled-streaming (projection) event processors** run on background
  threads with no HTTP caller to propagate a failure back to. They are
  configured with `ReportingErrorHandler` (not `PropagatingErrorHandler`
  directly — see `AxonEventProcessingConfig#pooledStreamingProcessor`),
  which calls `ErrorReporter.report(...)` (with a cooldown, configured via
  `application.error-reporting.axon-projection-cooldown`, default `5m`, to
  avoid retry-storm alerts) before delegating to the real
  `PropagatingErrorHandler` for Axon's normal rethrow/retry/abort semantics.
  Axon's synchronous "subscribing" command-side processors still use
  `PropagatingErrorHandler` directly: their failures propagate back through
  the originating command's call stack to the HTTP boundary and are reported
  there, so routing them through `ErrorReporter` again in processor code
  would double-report.
- **`WorkListProjection` and `PriorAuthorityHistoryProjection`** detect
  missing-entity conditions during event replay without throwing (so the
  processor can continue); these are reported via `ErrorReporter` rather
  than logged directly, for the same single-reporting-boundary reason as
  everywhere else.
- **`AssessmentReconciliation.run`** reports failures via `ErrorReporter` and
  deliberately does *not* rethrow: it runs on a `@Scheduled` thread, and
  Spring's own scheduler error handling already logs (at `ERROR`) any
  exception that escapes a `@Scheduled` method — rethrowing would cause the
  same failure to be reported twice via two different mechanisms, which
  `ErrorReporter`'s dedup marker cannot prevent because Spring's own handler
  is outside this codebase.

## Reference examples (already correct, cited rather than modified)

- `SdsRestClientConfig` — classifies outbound HTTP client failures: 4xx →
  `WARN`, 5xx → `ERROR`, `IOException` (network failure) → `ERROR`.
- `SecondaryAuthorizationFilter` — logs expected authorization failures at
  `WARN`.
- `DeletePriorAuthorityDocumentUseCase` — reports a residual failure via
  `ErrorReporter` after the primary outcome has already succeeded, because it
  is genuinely unexpected and otherwise invisible.

## Adding a new exception or handler

1. If the new failure is client-caused and expected (maps to a 4xx
   response), make the exception class `implements ClientFacingException`
   and log it at `WARN` in its `@ExceptionHandler` method.
2. If it is unexpected or operator-actionable (maps to a 5xx response, or
   has no HTTP caller), call `errorReporter.report(message, exception)`
   rather than logging directly.
3. Do not log `ERROR` directly outside `ErrorReporter`/`LoggingAspects` —
   `AxonArchitectureTest#onlyErrorReporterMayLogAtErrorLevel` will fail the
   build.
4. It is safe to call `errorReporter.report(...)` on an exception that may
   already have been reported by an inner boundary (e.g. a generic backstop
   handler) — the dedup marker makes the second call a no-op.
5. `ApplicationExceptionHandlerLoggingArchTest` will fail the build if a new
   `@ExceptionHandler` method does not call `log.warn(...)`, `log.error(...)`,
   or `ErrorReporter.report(...)`.
6. New `*DataStore` classes must depend on `ErrorReporter` — enforced by
   `AxonArchitectureTest#dataStoreClassesMustDependOnErrorReporter`.
