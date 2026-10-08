package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OfficeCodeWriteAccessAspectTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenProviderDoesNotOwnAggregateOffice_whenCallingAnnotatedMethod_thenDeniesBeforeBody() {
    authenticate("access_as_provider", List.of("1A001B"));
    ProtectedHandler handler = proxiedHandler();

    assertThatThrownBy(() -> handler.handle(new Resource("2B002C")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(handler.wasHandled()).isFalse();
  }

  @Test
  void givenProviderOwnsAggregateOffice_whenCallingAnnotatedMethod_thenExecutesBody() {
    authenticate("access_as_provider", List.of("1A001B"));
    ProtectedHandler handler = proxiedHandler();

    handler.handle(new Resource("1A001B"));

    assertThat(handler.wasHandled()).isTrue();
  }

  private ProtectedHandler proxiedHandler() {
    AspectJProxyFactory factory = new AspectJProxyFactory(new ProtectedHandler());
    factory.addAspect(new OfficeCodeWriteAccessAspect(new OfficeCodeWriteAccessPolicy()));
    return factory.getProxy();
  }

  private void authenticate(String scopes, List<String> officeCodes) {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("scp", scopes, "LAA_ACCOUNTS", officeCodes));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  static class ProtectedHandler {
    private boolean handled;

    @RequireOfficeCodeWriteAccess
    void handle(@OfficeCodeResource Resource resource) {
      handled = true;
    }

    boolean wasHandled() {
      return handled;
    }
  }

  record Resource(String officeCode) {}
}
