package uk.gov.justice.laa.dstew.access.shared.logging.aspects;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * AspectJ implementation for @LogMethodArguments.
 */
@Aspect
@Component
@Slf4j
public class LoggingAspects {

  /**
   * Before advice for @LogMethodArguments aspect.
   *
   * @param joinPoint AspectJ-provided join point.
   */
  @Before("@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments)")
  public void logMethodArgumentsAdvice(JoinPoint joinPoint) {
    if (log.isInfoEnabled()) {
      Object[] argumentsArray = Objects.requireNonNullElse(joinPoint.getArgs(), new Object[] {});
      String allMethodArguments =
          Arrays.stream(argumentsArray)
              .map(Object::toString)
              .collect(Collectors.joining(",", "[", "]"));

      String classAndMethodNames = getClassAndMethodName(joinPoint);

      log.info("{} methodArguments: {}", classAndMethodNames, allMethodArguments);
    }
  }

  /**
   * After returning advice for @LogMethodArguments aspect.
   *
   * @param joinPoint AspectJ-provided join point.
   * @param methodResponse the returned value.
   */
  @AfterReturning(
      value = "@annotation(uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse)",
      returning = "methodResponse")
  public void logMethodResponseAdvice(JoinPoint joinPoint, Object methodResponse) {
    if (log.isInfoEnabled()) {
      String classAndMethodName = getClassAndMethodName(joinPoint);

      log.info("{} methodResponse: {}", classAndMethodName, methodResponse);
    }
  }

  private String getClassAndMethodName(JoinPoint joinPoint) {
    return joinPoint.getSignature().toShortString();
  }
}
