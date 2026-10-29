package uk.gov.justice.laa.dstew.access.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.stream.IntStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Resolves command-side protected resources and authorises them before a handler runs. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WriteAccessAspect {
  private final ApplicationContext applicationContext;
  private final OfficeCodeWriteAccessPolicy policy;

  public WriteAccessAspect(
      ApplicationContext applicationContext, OfficeCodeWriteAccessPolicy policy) {
    this.applicationContext = applicationContext;
    this.policy = policy;
  }

  /** Resolves and authorizes the annotated handler's protected resources before proceeding. */
  @Around("@annotation(requireWriteAccess)")
  public Object requireWriteAccess(
      ProceedingJoinPoint joinPoint, RequireWriteAccess requireWriteAccess) throws Throwable {
    CallerCommand command = callerCommand(joinPoint);
    WriteAccessTargetResolver resolver = resolverFor(requireWriteAccess);
    Object resource = writeAccessResolverResource(joinPoint);
    policy.requireWriteAccess(resolver.resolve(resource), command.caller());
    return joinPoint.proceed();
  }

  private Object writeAccessResolverResource(ProceedingJoinPoint joinPoint) {
    Object resource = singleAnnotatedArgument(joinPoint, WriteAccessResolverResource.class);
    if (resource == null) {
      throw new IllegalStateException(
          "@WriteAccessResolverResource parameter must have a non-null value");
    }
    return resource;
  }

  private CallerCommand callerCommand(ProceedingJoinPoint joinPoint) {
    Object argument = singleAnnotatedArgument(joinPoint, AuthenticatedCallerResource.class);
    if (!(argument instanceof CallerCommand command)) {
      throw new IllegalStateException(
          "@AuthenticatedCallerResource parameter must implement CallerCommand");
    }
    if (command.caller() == null) {
      throw new IllegalStateException(
          "@AuthenticatedCallerResource CallerCommand must have a non-null caller");
    }
    return command;
  }

  private static Object singleAnnotatedArgument(
      ProceedingJoinPoint joinPoint, Class<? extends Annotation> annotation) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    List<Object> matches =
        IntStream.range(0, parameters.length)
            .filter(index -> parameters[index].isAnnotationPresent(annotation))
            .mapToObj(index -> joinPoint.getArgs()[index])
            .toList();
    if (matches.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly one @" + annotation.getSimpleName() + " parameter on " + method);
    }
    return matches.getFirst();
  }

  private WriteAccessTargetResolver resolverFor(RequireWriteAccess requireWriteAccess) {
    return applicationContext.getBean(requireWriteAccess.resolver());
  }
}
