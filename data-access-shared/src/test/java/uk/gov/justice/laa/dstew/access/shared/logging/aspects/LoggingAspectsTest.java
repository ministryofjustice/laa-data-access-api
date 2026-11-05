package uk.gov.justice.laa.dstew.access.shared.logging.aspects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** Verifies method logging content, truncation, and client-facing exception classification. */
class LoggingAspectsTest {

  private final LoggingAspects aspects = new LoggingAspects();
  private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspects.class);
  private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
  private Level originalLevel;
  private Map<String, String> originalMdc;

  @BeforeEach
  void attachLogAppender() {
    originalLevel = logger.getLevel();
    originalMdc = MDC.getCopyOfContextMap();
    logger.setLevel(Level.INFO);
    MDC.clear();
    logEvents.start();
    logger.addAppender(logEvents);
  }

  @AfterEach
  void detachLogAppender() {
    logger.detachAppender(logEvents);
    logEvents.stop();
    logger.setLevel(originalLevel);
    if (originalMdc == null) {
      MDC.clear();
    } else {
      MDC.setContextMap(originalMdc);
    }
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
  void givenArguments_whenInvoked_thenLogsNonNullArgumentsAndCorrelationId() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(new Object[] {"first", null, 42});
    MDC.put("correlationId", "request-123");

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage(
        "Method invoked: class=SomeController, method=someMethod, arguments=[first,42],"
            + " correlationId=request-123");
  }

  @Test
  void givenNullArguments_whenInvoked_thenLogsEmptyArguments() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(null);

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage(
        "Method invoked: class=SomeController, method=someMethod, arguments=[], correlationId=null");
  }

  @Test
  void givenEmptyArguments_whenInvoked_thenLogsEmptyArguments() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(new Object[] {});

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage(
        "Method invoked: class=SomeController, method=someMethod, arguments=[], correlationId=null");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"result"})
  void givenResponse_whenReturned_thenLogsResponseAndCorrelationId(String response) {
    MDC.put("correlationId", "request-123");

    aspects.logMethodResponseAdvice(joinPoint(), response);

    assertInfoMessage(
        "Method completed: class=SomeController, method=someMethod, response="
            + response
            + ", correlationId=request-123");
  }

  @ParameterizedTest
  @ValueSource(ints = {499, 500, 501})
  void givenLongValue_whenLogged_thenTruncatesOnlyAboveLimit(int length) {
    String value = "a".repeat(length);
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(new Object[] {value});

    aspects.logMethodArgumentsAdvice(joinPoint);
    aspects.logMethodResponseAdvice(joinPoint, value);

    String expected = length > 500 ? "a".repeat(500) + "... (truncated)" : value;
    assertThat(logEvents.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Method invoked: class=SomeController, method=someMethod, arguments=["
                + expected
                + "], correlationId=null",
            "Method completed: class=SomeController, method=someMethod, response="
                + expected
                + ", correlationId=null");
  }

  @Test
  void givenInfoDisabled_whenInvokedAndReturned_thenDoesNotInspectOrLogValues() {
    logger.setLevel(Level.WARN);
    JoinPoint joinPoint = mock(JoinPoint.class);
    Object response = mock(Object.class);

    aspects.logMethodArgumentsAdvice(joinPoint);
    aspects.logMethodResponseAdvice(joinPoint, response);

    assertThat(logEvents.list).isEmpty();
    verifyNoInteractions(joinPoint, response);
  }

  private void assertInfoMessage(String message) {
    assertThat(logEvents.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage()).isEqualTo(message);
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  private void assertWarnMessage(Throwable exception) {
    assertThat(logEvents.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "Method rejected request: class=SomeController, method=someMethod,"
                          + " exceptionType="
                          + exception.getClass().getSimpleName()
                          + ", exceptionMessage="
                          + exception.getMessage()
                          + ", correlationId=null");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void givenClientFacingException_whenThrown_thenLogsAtWarn() {
    Throwable exception = new SampleClientFacingException("already exists");
    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenIllegalArgumentException_whenThrown_thenLogsAtWarn() {
    Throwable exception = new IllegalArgumentException("bad input");
    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenAccessDeniedException_whenThrown_thenLogsAtWarn() {
    Throwable exception = new AccessDeniedException("no access");
    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenMethodArgumentNotValidException_whenThrown_thenLogsAtWarn() {
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);

    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenFrameworkClientFacingExceptionByName_whenThrown_thenLogsAtWarn() {
    Throwable exception = new EntityNotFoundException("aggregate missing");
    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenUnclassifiedRuntimeException_whenThrown_thenLogsAtError() {
    MDC.put("correlationId", "request-123");
    aspects.logMethodExceptionAdvice(joinPoint(), new RuntimeException("unexpected failure"));

    assertThat(logEvents.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "Method failed: class=SomeController, method=someMethod,"
                          + " exceptionType=RuntimeException, exceptionMessage=unexpected failure,"
                          + " correlationId=request-123");
              assertThat(event.getThrowableProxy()).isNotNull();
              assertThat(event.getThrowableProxy().getClassName())
                  .isEqualTo(RuntimeException.class.getName());
              assertThat(event.getThrowableProxy().getMessage()).isEqualTo("unexpected failure");
            });
  }
}
