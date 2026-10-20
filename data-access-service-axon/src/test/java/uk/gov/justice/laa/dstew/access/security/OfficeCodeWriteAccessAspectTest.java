package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.axonframework.modelling.repository.ManagedEntity;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class OfficeCodeWriteAccessAspectTest {

  @Test
  void givenProviderDoesNotOwnAggregateOffice_whenCallingAnnotatedMethod_thenDeniesBeforeBody() {
    ProtectedHandler handler = proxiedHandler();

    assertThatThrownBy(() -> handler.handle(new Resource("2B002C"), providerCaller("1A001B")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(handler.wasHandled()).isFalse();
  }

  @Test
  void givenProviderOwnsAggregateOffice_whenCallingAnnotatedMethod_thenExecutesBody() {
    ProtectedHandler handler = proxiedHandler();

    handler.handle(new Resource("1A001B"), providerCaller("1A001B"));

    assertThat(handler.wasHandled()).isTrue();
  }

  @Test
  void givenTwoAnnotatedResources_whenCallingAnnotatedMethod_thenRejectsInvalidHandlerShape() {
    TwoResourcesHandler handler = proxiedHandler(new TwoResourcesHandler());

    assertThatThrownBy(() -> handler.handle(new Resource("1A001B"), new Resource("1A001B")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @OfficeCodeResource parameter is permitted");
  }

  @Test
  void givenNoAnnotatedResource_whenCallingAnnotatedMethod_thenRejectsInvalidHandlerShape() {
    NoResourceHandler handler = proxiedHandler(new NoResourceHandler());

    assertThatThrownBy(() -> handler.handle(new Resource("1A001B")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @OfficeCodeResource parameter");
  }

  @Test
  void givenResourceWithoutOfficeCode_whenCallingAnnotatedMethod_thenRejectsInvalidResource() {
    NoOfficeCodeHandler handler = proxiedHandler(new NoOfficeCodeHandler());

    assertThatThrownBy(() -> handler.handle(new ResourceWithoutOfficeCode()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must expose a readable officeCode property");
  }

  @Test
  void
      givenResourceWithNonStringOfficeCode_whenCallingAnnotatedMethod_thenRejectsInvalidResource() {
    NonStringOfficeCodeHandler handler = proxiedHandler(new NonStringOfficeCodeHandler());

    assertThatThrownBy(() -> handler.handle(new ResourceWithNonStringOfficeCode(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("officeCode property must be a String");
  }

  @Test
  void givenCommandExposingCaller_whenCallingAnnotatedMethod_thenExecutesBody() {
    CallerPropertyHandler handler = proxiedHandler(new CallerPropertyHandler());

    handler.handle(new Resource("1A001B"), new CommandWithCaller(providerCaller("1A001B")));

    assertThat(handler.wasHandled()).isTrue();
  }

  @Test
  void givenCallerResourceWithoutCaller_whenCallingAnnotatedMethod_thenRejectsInvalidResource() {
    InvalidCallerPropertyHandler handler = proxiedHandler(new InvalidCallerPropertyHandler());

    assertThatThrownBy(() -> handler.handle(new Resource("1A001B"), new Object()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be an AuthenticatedCaller or expose a caller property");
  }

  @Test
  void
      givenTwoAnnotatedCallerResources_whenCallingAnnotatedMethod_thenRejectsInvalidHandlerShape() {
    TwoCallerResourcesHandler handler = proxiedHandler(new TwoCallerResourcesHandler());

    assertThatThrownBy(
            () ->
                handler.handle(
                    new Resource("1A001B"), providerCaller("1A001B"), providerCaller("1A001B")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only one @AuthenticatedCallerResource parameter is permitted");
  }

  @Test
  void givenNoAnnotatedCallerResource_whenCallingAnnotatedMethod_thenRejectsInvalidHandlerShape() {
    NoCallerResourceHandler handler = proxiedHandler(new NoCallerResourceHandler());

    assertThatThrownBy(() -> handler.handle(new Resource("1A001B"), providerCaller("1A001B")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare an @AuthenticatedCallerResource parameter");
  }

  @Test
  void
      givenManagedEntityWithMissingEntity_whenCallingAnnotatedMethod_thenThrowsNotFoundBeforeBody() {
    ManagedEntityResourceHandler handler = proxiedHandler(new ManagedEntityResourceHandler());

    assertThatThrownBy(
            () -> handler.handle(new FakeManagedEntity("id-1", null), providerCaller("1A001B")))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(handler.wasHandled()).isFalse();
  }

  @Test
  void givenManagedEntityWithPresentEntity_whenCallingAnnotatedMethod_thenExecutesBody() {
    ManagedEntityResourceHandler handler = proxiedHandler(new ManagedEntityResourceHandler());

    handler.handle(new FakeManagedEntity("id-1", new Resource("1A001B")), providerCaller("1A001B"));

    assertThat(handler.wasHandled()).isTrue();
  }

  private ProtectedHandler proxiedHandler() {
    return proxiedHandler(new ProtectedHandler());
  }

  private <T> T proxiedHandler(T handler) {
    AspectJProxyFactory factory = new AspectJProxyFactory(handler);
    factory.addAspect(new OfficeCodeWriteAccessAspect(new OfficeCodeWriteAccessPolicy()));
    return factory.getProxy();
  }

  private AuthenticatedCaller providerCaller(String officeCode) {
    return new AuthenticatedCaller(
        Optional.empty(), Set.of("access_as_provider"), Set.of(officeCode), Set.of());
  }

  static class ProtectedHandler {
    private boolean handled;

    @RequireOfficeCodeWriteAccess
    void handle(
        @OfficeCodeResource Resource resource,
        @AuthenticatedCallerResource AuthenticatedCaller caller) {
      handled = true;
    }

    boolean wasHandled() {
      return handled;
    }
  }

  record Resource(String officeCode) {}

  static class TwoResourcesHandler {
    @RequireOfficeCodeWriteAccess
    void handle(@OfficeCodeResource Resource first, @OfficeCodeResource Resource second) {}
  }

  static class NoResourceHandler {
    @RequireOfficeCodeWriteAccess
    void handle(Resource resource) {}
  }

  static class NoOfficeCodeHandler {
    @RequireOfficeCodeWriteAccess
    void handle(@OfficeCodeResource ResourceWithoutOfficeCode resource) {}
  }

  static class NonStringOfficeCodeHandler {
    @RequireOfficeCodeWriteAccess
    void handle(@OfficeCodeResource ResourceWithNonStringOfficeCode resource) {}
  }

  static class CallerPropertyHandler {
    private boolean handled;

    @RequireOfficeCodeWriteAccess
    void handle(
        @OfficeCodeResource Resource resource,
        @AuthenticatedCallerResource CommandWithCaller command) {
      handled = true;
    }

    boolean wasHandled() {
      return handled;
    }
  }

  static class InvalidCallerPropertyHandler {
    @RequireOfficeCodeWriteAccess
    void handle(
        @OfficeCodeResource Resource resource, @AuthenticatedCallerResource Object caller) {}
  }

  static class TwoCallerResourcesHandler {
    @RequireOfficeCodeWriteAccess
    void handle(
        @OfficeCodeResource Resource resource,
        @AuthenticatedCallerResource AuthenticatedCaller first,
        @AuthenticatedCallerResource AuthenticatedCaller second) {}
  }

  static class NoCallerResourceHandler {
    @RequireOfficeCodeWriteAccess
    void handle(@OfficeCodeResource Resource resource, AuthenticatedCaller caller) {}
  }

  record ResourceWithoutOfficeCode() {}

  record ResourceWithNonStringOfficeCode(Integer officeCode) {}

  record CommandWithCaller(AuthenticatedCaller caller) {}

  static class ManagedEntityResourceHandler {
    private boolean handled;

    @RequireOfficeCodeWriteAccess
    void handle(
        @OfficeCodeResource FakeManagedEntity resource,
        @AuthenticatedCallerResource AuthenticatedCaller caller) {
      handled = true;
    }

    boolean wasHandled() {
      return handled;
    }
  }

  record FakeManagedEntity(String identifier, Resource entity)
      implements ManagedEntity<String, Resource> {

    @Override
    public Resource applyStateChange(UnaryOperator<Resource> change) {
      throw new UnsupportedOperationException();
    }
  }
}
