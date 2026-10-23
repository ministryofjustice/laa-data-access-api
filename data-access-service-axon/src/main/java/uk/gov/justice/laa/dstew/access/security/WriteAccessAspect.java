package uk.gov.justice.laa.dstew.access.security;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Resolves command-side access targets and authorises them before a handler runs. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class WriteAccessAspect {
  private final List<WriteAccessTargetResolver<?, ?>> resolvers;
  private final List<WriteAccessAuthorizer<?>> authorizers;

  public WriteAccessAspect(
      List<WriteAccessTargetResolver<?, ?>> resolvers, List<WriteAccessAuthorizer<?>> authorizers) {
    this.resolvers = resolvers;
    this.authorizers = authorizers;
  }

  /** Resolves and authorizes the annotated handler's write targets before proceeding. */
  @Around("@annotation(requireWriteAccess)")
  public Object requireWriteAccess(
      ProceedingJoinPoint joinPoint, RequireWriteAccess requireWriteAccess) throws Throwable {
    CallerResource callerResource = callerResource(joinPoint);
    WriteAccessTargetResolver<Object, WriteAccessTarget> resolver = resolverFor(requireWriteAccess);
    WriteAccessAuthorizer<WriteAccessTarget> authorizer = authorizerFor(requireWriteAccess);
    WriteAccessTargetResolutionContext context =
        resolver.requiresHandlerResource() ? targetResolutionContext(joinPoint) : null;
    Object command = callerResource.command();
    if (!resolver.commandType().isInstance(command)) {
      throw new IllegalStateException(
          "WriteAccessTargetResolver "
              + resolver.getClass().getName()
              + " does not support command "
              + command.getClass().getName());
    }
    if (!authorizer.targetType().equals(resolver.targetType())) {
      throw new IllegalStateException(
          "WriteAccessAuthorizer "
              + authorizer.getClass().getName()
              + " does not support targets from "
              + resolver.getClass().getName());
    }
    authorizer.requireWriteAccess(resolver.resolve(command, context), callerResource.caller());
    return joinPoint.proceed();
  }

  private WriteAccessTargetResolutionContext targetResolutionContext(
      ProceedingJoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] arguments = joinPoint.getArgs();
    Object resource = null;
    AggregateResource resourceMetadata = null;

    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index].isAnnotationPresent(AggregateResource.class)) {
        if (resource != null) {
          throw new IllegalStateException(
              "Only one @AggregateResource parameter is permitted on " + method);
        }
        resource = arguments[index];
        resourceMetadata = parameters[index].getAnnotation(AggregateResource.class);
      }
    }
    if (resource == null) {
      throw new IllegalStateException(
          "@RequireWriteAccess method must declare an @AggregateResource parameter: " + method);
    }
    return new WriteAccessTargetResolutionContext(method, resource, resourceMetadata);
  }

  private CallerResource callerResource(ProceedingJoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] arguments = joinPoint.getArgs();
    CallerResource callerResource = null;
    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index].isAnnotationPresent(AuthenticatedCallerResource.class)) {
        if (callerResource != null) {
          throw new IllegalStateException(
              "Only one @AuthenticatedCallerResource parameter is permitted on " + method);
        }
        callerResource = new CallerResource(arguments[index], caller(arguments[index], method));
      }
    }
    if (callerResource == null) {
      throw new IllegalStateException(
          "@RequireWriteAccess method must declare an @AuthenticatedCallerResource parameter: "
              + method);
    }
    return callerResource;
  }

  private AuthenticatedCaller caller(Object argument, Method method) {
    if (argument instanceof AuthenticatedCaller authenticatedCaller) {
      return authenticatedCaller;
    }
    BeanWrapper beanWrapper = new BeanWrapperImpl(argument);
    if (beanWrapper.isReadableProperty("caller")
        && beanWrapper.getPropertyValue("caller") instanceof AuthenticatedCaller caller) {
      return caller;
    }
    throw new IllegalStateException(
        "@AuthenticatedCallerResource parameter must be an AuthenticatedCaller or expose a caller property: "
            + method);
  }

  @SuppressWarnings("unchecked")
  private WriteAccessTargetResolver<Object, WriteAccessTarget> resolverFor(
      RequireWriteAccess requireWriteAccess) {
    return (WriteAccessTargetResolver<Object, WriteAccessTarget>)
        resolvers.stream()
            .filter(resolver -> resolver.getClass().equals(requireWriteAccess.resolver()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No WriteAccessTargetResolver bean for "
                            + requireWriteAccess.resolver().getName()));
  }

  @SuppressWarnings("unchecked")
  private WriteAccessAuthorizer<WriteAccessTarget> authorizerFor(
      RequireWriteAccess requireWriteAccess) {
    return (WriteAccessAuthorizer<WriteAccessTarget>)
        authorizers.stream()
            .filter(authorizer -> authorizer.getClass().equals(requireWriteAccess.authorizer()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No WriteAccessAuthorizer bean for "
                            + requireWriteAccess.authorizer().getName()));
  }

  private record CallerResource(Object command, AuthenticatedCaller caller) {}
}
