package uk.gov.justice.laa.dstew.access.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorContext;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Wraps Axon's {@link PropagatingErrorHandler} so that pooled-streaming event-processor failures
 * are reported through {@link ErrorReporter}, exactly like every other unexpected failure.
 *
 * <p>Axon's pooled-streaming processors run on background threads with no HTTP caller to propagate
 * back to; their configured {@code PropagatingErrorHandler} only rethrows the failure, which the
 * owning {@code WorkPackage} then logs at {@code WARN} (not {@code ERROR}) before aborting the
 * segment. With {@code sentry-logback}'s {@code minimum-event-level: error}, that means these
 * failures previously produced <strong>no Sentry event at all</strong>. This class closes that gap
 * without changing Axon's existing rethrow/retry/abort semantics: it reports, then always delegates
 * to the real {@link PropagatingErrorHandler}.
 *
 * <p>A failure for a given {@code (eventProcessor, exceptionType)} pair is reported at most once
 * per {@link #cooldown} window, so a persistently failing segment that Axon keeps retrying doesn't
 * spam Sentry with one alert per retry. The cooldown is tracked in-memory, per instance — it is
 * deliberately not shared or distributed across replicas, since the goal is to avoid noisy
 * duplicate alerts, not to guarantee an exactly-once report across the whole fleet.
 */
@Component
public class ReportingErrorHandler implements ErrorHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ReportingErrorHandler.class);

  private final ErrorReporter errorReporter;
  private final ErrorHandler delegate;
  private final Clock clock;
  private final Duration cooldown;
  private final Map<String, Instant> lastReportedAt = new ConcurrentHashMap<>();

  /**
   * Creates the handler, delegating rethrow behaviour to the real {@code PropagatingErrorHandler}.
   */
  @Autowired
  public ReportingErrorHandler(
      ErrorReporter errorReporter,
      @Value("${application.error-reporting.axon-projection-cooldown:5m}") Duration cooldown) {
    this(errorReporter, PropagatingErrorHandler.instance(), Clock.systemUTC(), cooldown);
  }

  ReportingErrorHandler(
      ErrorReporter errorReporter, ErrorHandler delegate, Clock clock, Duration cooldown) {
    this.errorReporter = errorReporter;
    this.delegate = delegate;
    this.clock = clock;
    this.cooldown = cooldown;
  }

  @Override
  public void handleError(ErrorContext errorContext) throws Exception {
    try {
      maybeReport(errorContext);
    } catch (RuntimeException reportingFailure) {
      // A failure while reporting must never block Axon's own rethrow/retry/abort handling.
      LOG.warn("Failed to report Axon event-processing failure to Sentry", reportingFailure);
    }
    delegate.handleError(errorContext);
  }

  private void maybeReport(ErrorContext errorContext) {
    String key = cooldownKey(errorContext);
    Instant now = clock.instant();
    Instant reportedAt = lastReportedAt.get(key);
    if (reportedAt != null && now.isBefore(reportedAt.plus(cooldown))) {
      LOG.debug("Suppressing duplicate Axon error report within cooldown for {}", key);
      return;
    }
    lastReportedAt.put(key, now);
    errorReporter.report(
        "Event processor [%s] failed to process %d event(s)"
            .formatted(errorContext.eventProcessor(), errorContext.failedEvents().size()),
        errorContext.error());
  }

  private String cooldownKey(ErrorContext errorContext) {
    return errorContext.eventProcessor() + "::" + errorContext.error().getClass().getName();
  }
}
