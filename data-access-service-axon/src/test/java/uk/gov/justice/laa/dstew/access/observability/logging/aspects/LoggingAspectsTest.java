package uk.gov.justice.laa.dstew.access.observability.logging.aspects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.modelling.entity.EntityMissingForInstanceCommandHandlerException;
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
import uk.gov.justice.laa.dstew.access.observability.logging.ClientFacingException;

/** Verifies method logging sanitization and client-facing exception classification. */
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
  void givenArguments_whenInvoked_thenLogsNonNullArguments() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(new Object[] {"first", null, 42});

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage(
        "Method invoked: class=SomeController, method=someMethod, arguments=[String,42]");
  }

  @Test
  void givenNullArguments_whenInvoked_thenLogsEmptyArguments() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(null);

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage("Method invoked: class=SomeController, method=someMethod, arguments=[]");
  }

  @Test
  void givenEmptyArguments_whenInvoked_thenLogsEmptyArguments() {
    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs()).thenReturn(new Object[] {});

    aspects.logMethodArgumentsAdvice(joinPoint);

    assertInfoMessage("Method invoked: class=SomeController, method=someMethod, arguments=[]");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"result"})
  void givenResponse_whenReturned_thenLogsResponse(String response) {
    aspects.logMethodResponseAdvice(joinPoint(), response);

    String expected = response == null ? "null" : "String";

    assertInfoMessage(
        "Method completed: class=SomeController, method=someMethod, response=" + expected);
  }

  @Test
  void givenAllowlistedTypes_whenLogged_thenLogsValues() {
    UUID uuid = UUID.fromString("8d0ecf4d-5377-4cc1-9036-e4c0cd73a1dc");
    Instant instant = Instant.parse("2026-01-01T12:30:45Z");
    OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-01-01T12:30:45+01:00");
    ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-01-01T12:30:45+01:00[Europe/London]");

    JoinPoint joinPoint = joinPoint();
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {uuid, TestEnum.CASEWORKER, 7, true, instant, offsetDateTime});

    aspects.logMethodArgumentsAdvice(joinPoint);
    aspects.logMethodResponseAdvice(joinPoint, zonedDateTime);

    assertThat(logEvents.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Method invoked: class=SomeController, method=someMethod, arguments=["
                + uuid
                + ",CASEWORKER,7,true,"
                + instant
                + ","
                + offsetDateTime
                + "]",
            "Method completed: class=SomeController, method=someMethod, response=" + zonedDateTime);
  }

  @Test
  void givenExcludedAndContainerTypes_whenLogged_thenLogsTypeOnlyOrTypeAndSize() {
    JoinPoint joinPoint = joinPoint();
    ArrayList<String> listValue = new ArrayList<>(List.of("a", "b", "c"));
    LinkedHashMap<String, String> mapValue = new LinkedHashMap<>();
    mapValue.put("key1", "value1");
    mapValue.put("key2", "value2");
    when(joinPoint.getArgs())
        .thenReturn(
            new Object[] {"secret", LocalDate.now(), LocalDateTime.now(), listValue, mapValue});

    aspects.logMethodArgumentsAdvice(joinPoint);
    aspects.logMethodResponseAdvice(joinPoint, new SampleDto());

    assertThat(logEvents.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Method invoked: class=SomeController, method=someMethod, arguments="
                + "[String,LocalDate,LocalDateTime,ArrayList(size=3),LinkedHashMap(size=2)]",
            "Method completed: class=SomeController, method=someMethod, response=SampleDto");
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

  private enum TestEnum {
    CASEWORKER
  }

  private static class SampleDto {}

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
                          + exception.getMessage());
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
  void givenEntityNotFoundException_whenThrown_thenLogsAtWarn() {
    Throwable exception = new EntityNotFoundException("aggregate missing");
    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenEntityMissingForInstanceCommandHandlerException_whenThrown_thenLogsAtWarn() {
    CommandMessage command = mock(CommandMessage.class);
    when(command.type()).thenReturn(new MessageType(new QualifiedName("SomeCommand"), "0.0.1"));
    Throwable exception = new EntityMissingForInstanceCommandHandlerException(command);

    aspects.logMethodExceptionAdvice(joinPoint(), exception);

    assertWarnMessage(exception);
  }

  @Test
  void givenUnclassifiedRuntimeException_whenThrown_thenLogsAtError() {
    aspects.logMethodExceptionAdvice(joinPoint(), new RuntimeException("unexpected failure"));

    assertThat(logEvents.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .isEqualTo(
                      "Method failed: class=SomeController, method=someMethod,"
                          + " exceptionType=RuntimeException, exceptionMessage=unexpected"
                          + " failure");
              assertThat(event.getThrowableProxy()).isNotNull();
              assertThat(event.getThrowableProxy().getClassName())
                  .isEqualTo(RuntimeException.class.getName());
              assertThat(event.getThrowableProxy().getMessage()).isEqualTo("unexpected failure");
            });
  }
}
