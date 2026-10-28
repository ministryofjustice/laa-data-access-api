package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WriteAccessAspectTest {
  private final OfficeCodeWriteAccessPolicy policy = new OfficeCodeWriteAccessPolicy();

  @Test
  void givenAuthorisedCallerCommand_whenRequiringWriteAccess_thenProceeds() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    TestCommand command = new TestCommand(caller("0Z123A"));
    ProceedingJoinPoint joinPoint = joinPoint(method, command);
    TestResolver resolver = new TestResolver();

    new WriteAccessAspect(List.of(resolver), policy)
        .requireWriteAccess(joinPoint, annotation(method));

    verify(joinPoint).proceed();
    assertThat(resolver.resource).isSameAs(command);
  }

  @Test
  void givenDeniedCaller_whenRequiringWriteAccess_thenDoesNotProceed() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    ProceedingJoinPoint joinPoint = joinPoint(method, new TestCommand(caller("1A987X")));

    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(joinPoint, annotation(method)))
        .isInstanceOf(AccessDeniedException.class);

    verify(joinPoint, never()).proceed();
  }

  @Test
  void givenResourceBackedResolver_whenRequiringWriteAccess_thenPassesMarkedAggregate()
      throws Throwable {
    Method method =
        ResourceHandler.class.getDeclaredMethod("handle", Resource.class, TestCommand.class);
    Resource resource = new Resource("0Z123A");
    ProceedingJoinPoint joinPoint = joinPoint(method, resource, new TestCommand(caller("0Z123A")));
    ResourceBackedResolver resolver = new ResourceBackedResolver();

    new WriteAccessAspect(List.of(resolver), policy)
        .requireWriteAccess(joinPoint, annotation(method));

    verify(joinPoint).proceed();
    assertThat(resolver.resource).isSameAs(resource);
  }

  @Test
  void givenNoCallerMarker_whenRequiringWriteAccess_thenRejectsHandler() throws Throwable {
    Method method = MissingCallerHandler.class.getDeclaredMethod("handle", TestCommand.class);
    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(
                        joinPoint(method, new TestCommand(caller("0Z123A"))), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @AuthenticatedCallerResource");
  }

  @Test
  void givenNoResolverResourceMarker_whenRequiringWriteAccess_thenRejectsHandler()
      throws Throwable {
    Method method = MissingResourceHandler.class.getDeclaredMethod("handle", TestCommand.class);

    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(
                        joinPoint(method, new TestCommand(caller("0Z123A"))), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @WriteAccessResolverResource");
  }

  @Test
  void givenDuplicateResolverResourceMarkers_whenRequiringWriteAccess_thenRejectsHandler()
      throws Throwable {
    Method method =
        DuplicateResourceHandler.class.getDeclaredMethod("handle", TestCommand.class, Object.class);

    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(
                        joinPoint(method, new TestCommand(caller("0Z123A")), new Object()),
                        annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @WriteAccessResolverResource");
  }

  @Test
  void givenDuplicateCallerMarkers_whenRequiringWriteAccess_thenRejectsHandler() throws Throwable {
    Method method =
        DuplicateCallerHandler.class.getDeclaredMethod(
            "handle", TestCommand.class, TestCommand.class);
    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(
                        joinPoint(
                            method,
                            new TestCommand(caller("0Z123A")),
                            new TestCommand(caller("0Z123A"))),
                        annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @AuthenticatedCallerResource");
  }

  @Test
  void givenMarkedArgumentNotCallerCommand_whenRequiringWriteAccess_thenRejectsHandler()
      throws Throwable {
    Method method = InvalidCallerHandler.class.getDeclaredMethod("handle", Object.class);
    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(joinPoint(method, new Object()), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must implement CallerCommand");
  }

  @Test
  void givenNullCaller_whenRequiringWriteAccess_thenRejectsHandler() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(new TestResolver()), policy)
                    .requireWriteAccess(
                        joinPoint(method, new TestCommand(null)), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-null caller");
  }

  @Test
  void givenMissingResolverBean_whenRequiringWriteAccess_thenRejectsHandler() throws Throwable {
    Method method = TestHandler.class.getDeclaredMethod("handle", TestCommand.class);
    assertThatThrownBy(
            () ->
                new WriteAccessAspect(List.of(), policy)
                    .requireWriteAccess(
                        joinPoint(method, new TestCommand(caller("0Z123A"))), annotation(method)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No WriteAccessTargetResolver bean");
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

  private static AuthenticatedCaller caller(String officeCode) {
    return new AuthenticatedCaller(
        Optional.empty(), Set.of("access_as_provider"), Set.of(officeCode), Set.of());
  }

  private static class TestHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(@AuthenticatedCallerResource @WriteAccessResolverResource TestCommand command) {}
  }

  private static class ResourceHandler {
    @RequireWriteAccess(resolver = ResourceBackedResolver.class)
    void handle(
        @WriteAccessResolverResource Resource resource,
        @AuthenticatedCallerResource TestCommand command) {}
  }

  private static class MissingCallerHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(TestCommand command) {}
  }

  private static class MissingResourceHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(@AuthenticatedCallerResource TestCommand command) {}
  }

  private static class DuplicateResourceHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(
        @AuthenticatedCallerResource @WriteAccessResolverResource TestCommand command,
        @WriteAccessResolverResource Object resource) {}
  }

  private static class DuplicateCallerHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(
        @AuthenticatedCallerResource TestCommand first,
        @AuthenticatedCallerResource TestCommand second) {}
  }

  private static class InvalidCallerHandler {
    @RequireWriteAccess(resolver = TestResolver.class)
    void handle(@AuthenticatedCallerResource Object command) {}
  }

  private record TestCommand(AuthenticatedCaller caller) implements CallerCommand {}

  private record Resource(String officeCode) implements OfficeCodeOwned {
    public String getOfficeCode() {
      return officeCode;
    }
  }

  private static class TestResolver implements WriteAccessTargetResolver {
    private Object resource;

    @Override
    public List<ProtectedResource> resolve(Object resource) {
      this.resource = resource;
      return List.of(new ProtectedResource("0Z123A"));
    }
  }

  private static class ResourceBackedResolver extends OfficeCodeResourceWriteAccessResolver {
    private Object resource;

    @Override
    public List<ProtectedResource> resolve(Object resource) {
      this.resource = resource;
      return super.resolve(resource);
    }
  }
}
