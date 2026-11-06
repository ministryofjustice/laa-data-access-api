package uk.gov.justice.laa.dstew.access.shared.logging.aspects;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.gov.justice.laa.dstew.access.shared.logging.ClientFacingException;

/** AspectJ implementation for @LogMethodArguments and @LogMethodResponse annotations. */
@Aspect
@Component
@Slf4j
public class LoggingAspects {

  private static final String CORRELATION_ID_KEY = "correlationId";

  /**
   * Fully-qualified names of third-party exception types that represent expected, client-caused
   * failures but cannot implement {@link ClientFacingException} directly (they are defined outside
   * this codebase). Matched by name so this shared module does not need a compile-time dependency
   * on every framework that defines a "not found"/"conflict" style exception.
   */
  private static final Set<String> CLIENT_FACING_FRAMEWORK_TYPE_NAMES =
      Set.of(
          "org.axonframework.modelling.repository.EntityNotFoundException",
          "org.axonframework.modelling.entity.EntityMissingForInstanceCommandHandlerException");

  /**
   * Before advice for @LogMethodArguments aspect. Logs method entry with arguments in structured
   * format, including correlation ID for traceability.
   *
   * @param joinPoint AspectJ-provided join point.
   */
  @Before("@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments)")
  public void logMethodArgumentsAdvice(JoinPoint joinPoint) {
    if (log.isInfoEnabled()) {
      Object[] argumentsArray = Objects.requireNonNullElse(joinPoint.getArgs(), new Object[] {});
      String allMethodArguments =
          Arrays.stream(argumentsArray)
              .filter(arg -> arg != null)
              .map(this::sanitizeForLogging)
              .collect(Collectors.joining(",", "[", "]"));

      String correlationId = getCorrelationId();

      // Structured log with key-value pairs including correlation ID
      log.info(
          "Method invoked: class={}, method={}, arguments={}, correlationId={}",
          joinPoint.getSignature().getDeclaringTypeName(),
          joinPoint.getSignature().getName(),
          allMethodArguments,
          correlationId);
    }
  }

  /**
   * After returning advice for @LogMethodResponse aspect. Logs method exit with return value in
   * structured format, including correlation ID for traceability.
   *
   * @param joinPoint AspectJ-provided join point.
   * @param methodResponse the returned value.
   */
  @AfterReturning(
      value =
          "@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse)",
      returning = "methodResponse")
  public void logMethodResponseAdvice(JoinPoint joinPoint, Object methodResponse) {
    if (log.isInfoEnabled()) {
      String sanitizedResponse = sanitizeForLogging(methodResponse);
      String correlationId = getCorrelationId();

      log.info(
          "Method completed: class={}, method={}, response={}, correlationId={}",
          joinPoint.getSignature().getDeclaringTypeName(),
          joinPoint.getSignature().getName(),
          sanitizedResponse,
          correlationId);
    }
  }

  /**
   * After throwing advice for logging exceptions in annotated methods with correlation ID for
   * traceability.
   *
   * @param joinPoint AspectJ-provided join point.
   * @param exception the exception thrown.
   */
  @AfterThrowing(
      pointcut =
          "@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments) || "
              + "@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse)",
      throwing = "exception")
  public void logMethodExceptionAdvice(JoinPoint joinPoint, Throwable exception) {
    String correlationId = getCorrelationId();

    if (isClientFacing(exception)) {
      // Expected, client-caused outcome (maps to an HTTP 4xx response): breadcrumb-level only,
      // not a Sentry event. See ClientFacingException for the agreed reporting rule.
      log.warn(
          "Method rejected request: class={}, method={}, exceptionType={}, exceptionMessage={},"
              + " correlationId={}",
          joinPoint.getSignature().getDeclaringTypeName(),
          joinPoint.getSignature().getName(),
          exception.getClass().getSimpleName(),
          exception.getMessage(),
          correlationId);
      return;
    }

    // Unexpected/operator-actionable failure: ERROR triggers a Sentry event via the log route.
    log.error(
        "Method failed: class={}, method={}, exceptionType={}, exceptionMessage={}, correlationId={}",
        joinPoint.getSignature().getDeclaringTypeName(),
        joinPoint.getSignature().getName(),
        exception.getClass().getSimpleName(),
        exception.getMessage(),
        correlationId,
        exception);
  }

  /**
   * Decides whether a thrown exception represents an expected, client-caused failure that should
   * not be escalated to {@code ERROR}/Sentry. See {@link ClientFacingException} for the rule.
   *
   * @param exception the exception thrown by the annotated method
   * @return true if the exception is client-facing and should be logged at {@code WARN}
   */
  private boolean isClientFacing(Throwable exception) {
    return exception instanceof ClientFacingException
        || exception instanceof IllegalArgumentException
        || exception instanceof AccessDeniedException
        || exception instanceof MethodArgumentNotValidException
        || CLIENT_FACING_FRAMEWORK_TYPE_NAMES.contains(exception.getClass().getName());
  }

  /**
   * Gets the correlation ID from the MDC (Mapped Diagnostic Context). This allows the aspect to
   * include correlation IDs in logs without tight coupling to LoggingContext.
   *
   * @return the correlation ID, or null if not set
   */
  private String getCorrelationId() {
    return MDC.get(CORRELATION_ID_KEY);
  }

  /**
   * Sanitizes objects for logging to prevent logging sensitive information. This method should be
   * extended to mask PII and sensitive fields.
   *
   * @param obj the object to sanitize
   * @return sanitized string representation
   */
  private String sanitizeForLogging(Object obj) {
    if (obj == null) {
      return "null";
    }

    // Convert to string and truncate if too long to avoid massive log entries
    String str = obj.toString();
    final int maxLength = 500;
    if (str.length() > maxLength) {
      return str.substring(0, maxLength) + "... (truncated)";
    }

    return str;
  }
}
