package uk.gov.justice.laa.dstew.access.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ErrorReporterTest {

  private final ErrorReporter errorReporter = new ErrorReporter();

  @Test
  void givenFreshThrowable_whenReported_thenLogsAtErrorLevel() {
    RuntimeException failure = new RuntimeException("boom");
    Logger logger = (Logger) LoggerFactory.getLogger(ErrorReporter.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      errorReporter.report("something failed", failure);

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("something failed");
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
              });
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }
  }

  @Test
  void givenAlreadyReportedThrowable_whenReportedAgain_thenDoesNotLogTwice() {
    RuntimeException failure = new RuntimeException("boom");
    Logger logger = (Logger) LoggerFactory.getLogger(ErrorReporter.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      errorReporter.report("first report", failure);
      errorReporter.report("second report of the same instance", failure);

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo("first report"));
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }
  }

  @Test
  void givenThrowableWithUnrelatedSuppressedExceptions_whenReported_thenStillLogs() {
    RuntimeException failure = new RuntimeException("boom");
    failure.addSuppressed(new IllegalStateException("unrelated suppressed exception"));
    Logger logger = (Logger) LoggerFactory.getLogger(ErrorReporter.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      errorReporter.report("something failed", failure);

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(
              event -> assertThat(event.getFormattedMessage()).isEqualTo("something failed"));
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }
  }

  @Test
  void
      givenAlreadyReportedThrowableWithOtherSuppressedExceptions_whenReportedAgain_thenDoesNotLogTwice() {
    RuntimeException failure = new RuntimeException("boom");
    failure.addSuppressed(new IllegalStateException("unrelated suppressed exception"));
    Logger logger = (Logger) LoggerFactory.getLogger(ErrorReporter.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      errorReporter.report("first report", failure);
      errorReporter.report("second report of the same instance", failure);

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(event -> assertThat(event.getFormattedMessage()).isEqualTo("first report"));
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }
  }
}
