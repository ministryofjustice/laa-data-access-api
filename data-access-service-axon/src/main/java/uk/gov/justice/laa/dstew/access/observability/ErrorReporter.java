package uk.gov.justice.laa.dstew.access.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a failure becomes a Sentry event.
 *
 * <p>Call {@link #report(String, Throwable)} wherever an unexpected failure is detected — whether
 * it is then rethrown, swallowed, or converted into an HTTP response. A given throwable instance is
 * only ever reported once: if an inner boundary has already reported it, an outer boundary that
 * later catches the same instance again (for example a generic {@code @ExceptionHandler} acting as
 * a backstop) can call {@link #report(String, Throwable)} defensively without producing a duplicate
 * Sentry event.
 *
 * <p>Expected, client-caused failures (validation errors, conflicts, authentication rejections)
 * must <em>not</em> be routed through this method — log them at {@code INFO}/{@code WARN}/{@code
 * DEBUG} as appropriate, so they remain breadcrumbs rather than Sentry events.
 *
 * <p>Axon event/command processors are a deliberate, documented exception to this rule: they use
 * {@code PropagatingErrorHandler}, so failures are rethrown and logged at {@code ERROR} by Axon's
 * own framework logging, which {@code sentry-logback} already picks up regardless of call site.
 * There is no need to route those failures through this method as well.
 */
@Component
@Slf4j
public class ErrorReporter {

  /**
   * Logs the throwable at {@code ERROR} — forwarded to Sentry as an event by {@code sentry-logback}
   * — unless it has already been reported.
   *
   * @param message a human-readable description of the failure and its context
   * @param throwable the failure to report
   */
  public void report(String message, Throwable throwable) {
    if (alreadyReported(throwable)) {
      return;
    }
    throwable.addSuppressed(ReportedMarker.INSTANCE);
    log.error(message, throwable);
  }

  private boolean alreadyReported(Throwable throwable) {
    for (Throwable suppressed : throwable.getSuppressed()) {
      if (suppressed == ReportedMarker.INSTANCE) {
        return true;
      }
    }
    return false;
  }

  /** Cheap, stack-trace-free marker suppressed onto a throwable once it has been reported. */
  private static final class ReportedMarker extends RuntimeException {

    private static final ReportedMarker INSTANCE = new ReportedMarker();

    private ReportedMarker() {
      super(null, null, false, false);
    }
  }
}
