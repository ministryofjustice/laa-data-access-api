package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ApplicationReadScopeAuthorizationManagerTest {

  private final ApplicationReadScopeAuthorizationManager authorizationManager =
      new ApplicationReadScopeAuthorizationManager();

  @Test
  void givenUserScope_whenAuthorized_thenGrantsAccess() {
    assertThat(authorize(jwtAuthentication("access_as_user"))).isTrue();
  }

  @Test
  void givenProviderScope_whenAuthorized_thenGrantsAccess() {
    assertThat(authorize(jwtAuthentication("access_as_provider"))).isTrue();
  }

  @Test
  void givenBothSupportedScopes_whenAuthorized_thenGrantsAccess() {
    assertThat(authorize(jwtAuthentication("access_as_user access_as_provider"))).isTrue();
  }

  @Test
  void givenMissingScope_whenAuthorized_thenDeniesAccess() {
    assertThat(authorize(jwtAuthentication(null))).isFalse();
  }

  @Test
  void givenUnrelatedScope_whenAuthorized_thenDeniesAccess() {
    assertThat(authorize(jwtAuthentication("unrelated_scope"))).isFalse();
  }

  @Test
  void givenNonJwtAuthentication_whenAuthorized_thenDeniesAccess() {
    assertThat(authorize(new TestingAuthenticationToken("user", "credentials"))).isFalse();
  }

  private boolean authorize(Authentication authentication) {
    return authorizationManager.authorize(() -> authentication, null).isGranted();
  }

  private JwtAuthenticationToken jwtAuthentication(String scope) {
    Map<String, Object> claims =
        scope == null
            ? Map.of("sub", "caseworker")
            : Map.of(ApplicationReadScopeAuthorizationManager.SCOPES_CLAIM, scope);
    Jwt jwt =
        new Jwt(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    return new JwtAuthenticationToken(jwt, List.of());
  }
}
