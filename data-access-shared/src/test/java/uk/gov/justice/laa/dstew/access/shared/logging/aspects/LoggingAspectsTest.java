package uk.gov.justice.laa.dstew.access.shared.logging.aspects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/**
 * Verifies {@link LoggingAspects#logMethodExceptionAdvice(JoinPoint, Throwable)} classifies
 * exceptions per the agreed Sentry reporting rule: client-facing/expected failures log at {@code
 * WARN} (breadcrumb only), everything else logs at {@code ERROR} (Sentry event).
 */
class LoggingAspectsTest {

  private final LoggingAspects aspects = new LoggingAspects();
  private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspects.class);
  private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

  @BeforeEach
  void attachLogAppender() {
    logEvents.start();
    logger.addAppender(logEvents);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logEvents);
    logEvents.stop();
  }

  private JoinPoint joinPoint() {
    Signature signature = mock(Signature.class);
    when(signature.getDeclaringTypeName()).thenReturn("SomeController");
    when(signature.getName()).thenReturn("someMethod");
    JoinPoint joinPoint = mock(JoinPoint.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    return joinPoint;
  }

  private static class SampleClientFacingException extends RuntimeException
      implements ClientFacingException {
    SampleClientFacingException(String message) {
      super(message);
    }
  }

  @Test
  void givenClientFacingException_whenThrown_thenLogsAtWarn() {
    aspects.logMethodExceptionAdvice(
        joinPoint(), new SampleClientFacingException("already exists"));

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
  }

  @Test
  void givenIllegalArgumentException_whenThrown_thenLogsAtWarn() {
    aspects.logMethodExceptionAdvice(joinPoint(), new IllegalArgumentException("bad input"));

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
  }

  @Test
  void givenAccessDeniedException_whenThrown_thenLogsAtWarn() {
    aspects.logMethodExceptionAdvice(joinPoint(), new AccessDeniedException("no access"));

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
  }

  @Test
  void givenMethodArgumentNotValidException_whenThrown_thenLogsAtWarn() {
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);

    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
  }

  @Test
  void givenFrameworkClientFacingExceptionByName_whenThrown_thenLogsAtWarn() {
    aspects.logMethodExceptionAdvice(joinPoint(), new EntityNotFoundException("aggregate missing"));

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.WARN);
  }

  @Test
  void givenUnclassifiedRuntimeException_whenThrown_thenLogsAtError() {
    aspects.logMethodExceptionAdvice(joinPoint(), new RuntimeException("unexpected failure"));

    assertThat(logEvents.list)
        .singleElement()
        .extracting(ILoggingEvent::getLevel)
        .isEqualTo(Level.ERROR);
  }
}
