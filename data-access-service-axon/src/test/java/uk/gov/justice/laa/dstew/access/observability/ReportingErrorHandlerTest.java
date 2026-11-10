package uk.gov.justice.laa.dstew.access.observability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorContext;
import org.axonframework.messaging.eventhandling.processing.errorhandling.ErrorHandler;
import org.junit.jupiter.api.Test;

class ReportingErrorHandlerTest {

  private static final Duration COOLDOWN = Duration.ofMinutes(5);

  @Test
  void givenFirstFailure_whenHandled_thenReportsAndDelegates() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);
    RuntimeException failure = new RuntimeException("projection failed");
    ErrorContext errorContext = errorContext("application-projection", failure);

    handler.handleError(errorContext);

    verify(errorReporter).report(anyString(), same(failure));
    verify(delegate).handleError(errorContext);
  }

  @Test
  void givenSecondFailureWithinCooldown_whenHandled_thenDoesNotReportAgainButStillDelegates()
      throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    Instant start = Instant.EPOCH;
    MutableClock clock = new MutableClock(start);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(errorReporter, delegate, clock, COOLDOWN);
    ErrorContext firstFailure =
        errorContext("application-projection", new RuntimeException("first failure"));
    ErrorContext secondFailure =
        errorContext("application-projection", new RuntimeException("second failure"));

    handler.handleError(firstFailure);
    clock.advance(Duration.ofMinutes(1));
    handler.handleError(secondFailure);

    verify(errorReporter, times(1)).report(anyString(), any());
    verify(delegate).handleError(firstFailure);
    verify(delegate).handleError(secondFailure);
  }

  @Test
  void givenDifferentProcessor_whenHandled_thenReportsIndependently() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);
    RuntimeException failure = new RuntimeException("failed");

    handler.handleError(errorContext("application-projection", failure));
    handler.handleError(errorContext("work-list-projection", failure));

    verify(errorReporter, times(2)).report(anyString(), same(failure));
  }

  @Test
  void givenDifferentExceptionType_whenHandled_thenReportsIndependently() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);

    handler.handleError(errorContext("application-projection", new IllegalStateException("a")));
    handler.handleError(errorContext("application-projection", new IllegalArgumentException("b")));

    verify(errorReporter, times(2)).report(anyString(), any());
  }

  @Test
  void givenCooldownElapsed_whenHandled_thenReportsAgain() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    Instant start = Instant.EPOCH;
    MutableClock clock = new MutableClock(start);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(errorReporter, delegate, clock, COOLDOWN);
    RuntimeException failure = new RuntimeException("persistent failure");

    handler.handleError(errorContext("application-projection", failure));
    clock.advance(COOLDOWN.plusSeconds(1));
    handler.handleError(errorContext("application-projection", failure));

    verify(errorReporter, times(2)).report(anyString(), same(failure));
  }

  @Test
  void givenDelegateThrows_whenHandled_thenExceptionPropagates() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    RuntimeException failure = new RuntimeException("will abort segment");
    doThrow(failure).when(delegate).handleError(any());
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);

    assertThatThrownBy(() -> handler.handleError(errorContext("application-projection", failure)))
        .isSameAs(failure);
    verify(errorReporter).report(anyString(), same(failure));
  }

  @Test
  void givenReportingThrows_whenHandled_thenDelegateIsStillInvoked() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    doThrow(new RuntimeException("sentry unavailable"))
        .when(errorReporter)
        .report(anyString(), any());
    ErrorHandler delegate = mock(ErrorHandler.class);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);
    ErrorContext errorContext = errorContext("application-projection", new RuntimeException());

    handler.handleError(errorContext);

    verify(delegate).handleError(errorContext);
  }

  @Test
  void givenMultipleFailedEvents_whenHandled_thenReportsOnlyOnce() throws Exception {
    ErrorReporter errorReporter = mock(ErrorReporter.class);
    ErrorHandler delegate = mock(ErrorHandler.class);
    ReportingErrorHandler handler =
        new ReportingErrorHandler(
            errorReporter, delegate, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), COOLDOWN);
    ErrorContext errorContext =
        new ErrorContext(
            "application-projection", new RuntimeException("failed"), List.of(), Context.empty());

    handler.handleError(errorContext);

    verify(errorReporter, times(1)).report(anyString(), any());
  }

  private ErrorContext errorContext(String eventProcessor, Throwable error) {
    return new ErrorContext(eventProcessor, error, List.of(), Context.empty());
  }

  /** A mutable {@link Clock} test double so cooldown expiry can be exercised deterministically. */
  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
