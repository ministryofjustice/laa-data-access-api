package uk.gov.justice.laa.dstew.access.security;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Resolves command-side protected resources and authorises them before a handler runs. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WriteAccessAspect {
  private final List<WriteAccessTargetResolver> resolvers;
  private final OfficeCodeWriteAccessPolicy policy;

  public WriteAccessAspect(
      List<WriteAccessTargetResolver> resolvers, OfficeCodeWriteAccessPolicy policy) {
    this.resolvers = resolvers;
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
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] arguments = joinPoint.getArgs();
    Object resource = null;

    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index].isAnnotationPresent(WriteAccessResolverResource.class)) {
        if (resource != null) {
          throw new IllegalStateException(
              "Only one @WriteAccessResolverResource parameter is permitted on " + method);
        }
        resource = arguments[index];
      }
    }
    if (resource == null) {
      throw new IllegalStateException(
          "@RequireWriteAccess method must declare an @WriteAccessResolverResource parameter: "
              + method);
    }
    return resource;
  }

  private CallerCommand callerCommand(ProceedingJoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] arguments = joinPoint.getArgs();
    CallerCommand callerCommand = null;
    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index].isAnnotationPresent(AuthenticatedCallerResource.class)) {
        if (callerCommand != null) {
          throw new IllegalStateException(
              "Only one @AuthenticatedCallerResource parameter is permitted on " + method);
        }
        if (!(arguments[index] instanceof CallerCommand command)) {
          throw new IllegalStateException(
              "@AuthenticatedCallerResource parameter must implement CallerCommand: " + method);
        }
        AuthenticatedCaller caller = command.caller();
        if (caller == null) {
          throw new IllegalStateException(
              "@AuthenticatedCallerResource CallerCommand must have a non-null caller: " + method);
        }
        callerCommand = command;
      }
    }
    if (callerCommand == null) {
      throw new IllegalStateException(
          "@RequireWriteAccess method must declare an @AuthenticatedCallerResource parameter: "
              + method);
    }
    return callerCommand;
  }

  private WriteAccessTargetResolver resolverFor(RequireWriteAccess requireWriteAccess) {
    return resolvers.stream()
        .filter(resolver -> resolver.getClass().equals(requireWriteAccess.resolver()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No WriteAccessTargetResolver bean for "
                        + requireWriteAccess.resolver().getName()));
  }

}
