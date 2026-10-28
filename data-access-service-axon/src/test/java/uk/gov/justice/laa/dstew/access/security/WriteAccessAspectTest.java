package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class WriteAccessAspectTest {
  @Test
  void givenDeniedAuthorisation_whenRequiringWriteAccess_thenDoesNotProceedToTheHandler()
      throws Throwable {
    var resolver = new TestResolver();
    var authorizer = new OfficeCodeWriteAccessAuthorizer(new OfficeCodeWriteAccessPolicy());
    var aspect = new WriteAccessAspect(List.of(resolver), List.of(authorizer));
    var command =
        new TestCommand(
            new AuthenticatedCaller(
                Optional.empty(), Set.of("access_as_provider"), Set.of(), Set.of()));
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    when(joinPoint.getArgs()).thenReturn(new Object[] {command});
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint, method.getAnnotation(RequireWriteAccess.class)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verify(joinPoint, never()).proceed();
  }

  @Test
  void givenAuthorisedCallerAndHandlerResource_whenRequiringWriteAccess_thenProceedsToTheHandler()
      throws Throwable {
    var resolver = new OfficeCodeResourceWriteAccessResolver();
    var authorizer = new OfficeCodeWriteAccessAuthorizer(new OfficeCodeWriteAccessPolicy());
    var aspect = new WriteAccessAspect(List.of(resolver), List.of(authorizer));
    var command =
        new TestCommand(
            new AuthenticatedCaller(
                Optional.empty(), Set.of("access_as_provider"), Set.of("0Z123A"), Set.of()));
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    Method method =
        ResourceHandler.class.getDeclaredMethod("handle", Resource.class, TestCommand.class);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {new Resource(UUID.randomUUID(), "0Z123A"), command});
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);

    aspect.requireWriteAccess(joinPoint, method.getAnnotation(RequireWriteAccess.class));

    verify(joinPoint).proceed();
  }

  @Test
  void givenUnauthorisedCallerAndHandlerResource_whenRequiringWriteAccess_thenDoesNotProceedToTheHandler()
      throws Throwable {
    var resolver = new OfficeCodeResourceWriteAccessResolver();
    var authorizer = new OfficeCodeWriteAccessAuthorizer(new OfficeCodeWriteAccessPolicy());
    var aspect = new WriteAccessAspect(List.of(resolver), List.of(authorizer));
    var command =
        new TestCommand(
            new AuthenticatedCaller(
                Optional.empty(), Set.of("access_as_provider"), Set.of("1A987X"), Set.of()));
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    Method method =
        ResourceHandler.class.getDeclaredMethod("handle", Resource.class, TestCommand.class);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {new Resource(UUID.randomUUID(), "0Z123A"), command});
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint, method.getAnnotation(RequireWriteAccess.class)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verify(joinPoint, never()).proceed();
  }

  @Test
  void givenCommandUnsupportedByConfiguredResolver_whenRequiringWriteAccess_thenRejectsIt()
      throws Throwable {
    Method method = UnsupportedCommandHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect =
        new WriteAccessAspect(List.of(new StringResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not support command");
  }

  @Test
  void givenAuthorizerForDifferentTargetType_whenRequiringWriteAccess_thenRejectsIt()
      throws Throwable {
    Method method = MismatchedTargetHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect =
        new WriteAccessAspect(List.of(new TestResolver()), List.of(new OtherTargetAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not support targets from");
  }

  @Test
  void givenMultipleAggregateResources_whenRequiringWriteAccess_thenRejectsTheHandler()
      throws Throwable {
    Method method =
        DuplicateResourceHandler.class.getDeclaredMethod(
            "handle", Resource.class, Resource.class, TestCommand.class);
    var aspect =
        new WriteAccessAspect(
            List.of(new OfficeCodeResourceWriteAccessResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, resource(), resource(), new TestCommand(caller())),
                    annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @AggregateResource parameter");
  }

  @Test
  void givenResourceResolverWithoutAggregateResource_whenRequiringWriteAccess_thenRejectsTheHandler()
      throws Throwable {
    Method method = MissingResourceHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect =
        new WriteAccessAspect(
            List.of(new OfficeCodeResourceWriteAccessResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @AggregateResource parameter");
  }

  @Test
  void givenMultipleAuthenticatedCallerResources_whenRequiringWriteAccess_thenRejectsTheHandler()
      throws Throwable {
    Method method =
        DuplicateCallerHandler.class.getDeclaredMethod(
            "handle", TestCommand.class, TestCommand.class);
    var aspect =
        new WriteAccessAspect(List.of(new TestResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller()), new TestCommand(caller())),
                    annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @AuthenticatedCallerResource parameter");
  }

  @Test
  void givenNoAuthenticatedCallerResource_whenRequiringWriteAccess_thenRejectsTheHandler()
      throws Throwable {
    Method method = MissingCallerHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect =
        new WriteAccessAspect(List.of(new TestResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @AuthenticatedCallerResource parameter");
  }

  @Test
  void givenCallerResourceWithoutAuthenticatedCaller_whenRequiringWriteAccess_thenRejectsTheHandler()
      throws Throwable {
    Method method = InvalidCallerHandler.class.getDeclaredMethod("handle", Object.class);
    var aspect =
        new WriteAccessAspect(List.of(new TestResolver()), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () -> aspect.requireWriteAccess(joinPoint(method, new Object()), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be an AuthenticatedCaller or expose a caller property");
  }

  @Test
  void givenNoResolverBean_whenRequiringWriteAccess_thenRejectsTheHandler() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect = new WriteAccessAspect(List.of(), List.of(officeCodeAuthorizer()));

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No WriteAccessTargetResolver bean");
  }

  @Test
  void givenNoAuthorizerBean_whenRequiringWriteAccess_thenRejectsTheHandler() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    var aspect = new WriteAccessAspect(List.of(new TestResolver()), List.of());

    assertThatThrownBy(
            () ->
                aspect.requireWriteAccess(
                    joinPoint(method, new TestCommand(caller())), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No WriteAccessAuthorizer bean");
  }

  private static ProceedingJoinPoint joinPoint(Method method, Object... arguments) {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    when(joinPoint.getArgs()).thenReturn(arguments);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    return joinPoint;
  }

  private static RequireWriteAccess annotation(Method method) {
    return method.getAnnotation(RequireWriteAccess.class);
  }

  private static AuthenticatedCaller caller() {
    return new AuthenticatedCaller(
        Optional.empty(), Set.of("access_as_provider"), Set.of("0Z123A"), Set.of());
  }

  private static Resource resource() {
    return new Resource(UUID.randomUUID(), "0Z123A");
  }

  private static OfficeCodeWriteAccessAuthorizer officeCodeAuthorizer() {
    return new OfficeCodeWriteAccessAuthorizer(new OfficeCodeWriteAccessPolicy());
  }

  private static class TestHandler {
    @RequireWriteAccess(
        resolver = TestResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(@AuthenticatedCallerResource TestCommand command) {}
  }

  private static class ResourceHandler {
    @RequireWriteAccess(
        resolver = OfficeCodeResourceWriteAccessResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(
        @AggregateResource(resourceType = "test-resource", resourceIdProperty = "id")
            Resource resource,
        @AuthenticatedCallerResource TestCommand command) {}
  }

  private static class UnsupportedCommandHandler {
    @RequireWriteAccess(
        resolver = StringResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(@AuthenticatedCallerResource TestCommand command) {}
  }

  private static class MismatchedTargetHandler {
    @RequireWriteAccess(resolver = TestResolver.class, authorizer = OtherTargetAuthorizer.class)
    void handle(@AuthenticatedCallerResource TestCommand command) {}
  }

  private static class DuplicateResourceHandler {
    @RequireWriteAccess(
        resolver = OfficeCodeResourceWriteAccessResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(
        @AggregateResource(resourceType = "test", resourceIdProperty = "id") Resource first,
        @AggregateResource(resourceType = "test", resourceIdProperty = "id") Resource second,
        @AuthenticatedCallerResource TestCommand command) {}
  }

  private static class MissingResourceHandler {
    @RequireWriteAccess(
        resolver = OfficeCodeResourceWriteAccessResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(@AuthenticatedCallerResource TestCommand command) {}
  }

  private static class DuplicateCallerHandler {
    @RequireWriteAccess(
        resolver = TestResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(
        @AuthenticatedCallerResource TestCommand first,
        @AuthenticatedCallerResource TestCommand second) {}
  }

  private static class MissingCallerHandler {
    @RequireWriteAccess(
        resolver = TestResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(TestCommand command) {}
  }

  private static class InvalidCallerHandler {
    @RequireWriteAccess(
        resolver = TestResolver.class,
        authorizer = OfficeCodeWriteAccessAuthorizer.class)
    void handle(@AuthenticatedCallerResource Object command) {}
  }

  private record TestCommand(AuthenticatedCaller caller) {}

  private record Resource(UUID id, String officeCode) {}

  private static class TestResolver
      implements WriteAccessTargetResolver<TestCommand, OfficeCodeWriteAccessTarget> {
    @Override
    public Class<TestCommand> commandType() {
      return TestCommand.class;
    }

    @Override
    public Class<OfficeCodeWriteAccessTarget> targetType() {
      return OfficeCodeWriteAccessTarget.class;
    }

    @Override
    public List<OfficeCodeWriteAccessTarget> resolve(
        TestCommand command, WriteAccessTargetResolutionContext context) {
      return List.of(new OfficeCodeWriteAccessTarget("test-resource", UUID.randomUUID(), "0Z123A"));
    }
  }

  private static class StringResolver
      implements WriteAccessTargetResolver<String, OfficeCodeWriteAccessTarget> {
    @Override
    public Class<String> commandType() {
      return String.class;
    }

    @Override
    public Class<OfficeCodeWriteAccessTarget> targetType() {
      return OfficeCodeWriteAccessTarget.class;
    }

    @Override
    public List<OfficeCodeWriteAccessTarget> resolve(
        String command, WriteAccessTargetResolutionContext context) {
      return List.of();
    }
  }

  private record OtherTarget(String resourceType, UUID resourceId) implements WriteAccessTarget {}

  private static class OtherTargetAuthorizer implements WriteAccessAuthorizer<OtherTarget> {
    @Override
    public Class<OtherTarget> targetType() {
      return OtherTarget.class;
    }

    @Override
    public void requireWriteAccess(List<OtherTarget> targets, AuthenticatedCaller caller) {}
  }
}
