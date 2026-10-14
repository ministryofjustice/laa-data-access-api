package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedCallerFactoryTest {

  private final AuthenticatedCallerFactory factory = new AuthenticatedCallerFactory();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenAuthenticatedToken_whenCurrentCaller_thenProjectsNormalisedIdentityAndEntitlements() {
    UUID actorId = UUID.randomUUID();
    authenticate(
        Map.of(
            "oid", actorId.toString(),
            "scp", "access_as_provider access_as_provider",
            "LAA_ACCOUNTS", List.of(" 1A001B ", ""),
            "LAA_APP_ROLES", List.of(" LAA_PROVIDER ", "")));

    AuthenticatedCaller caller = factory.currentCaller();

    assertThat(caller.actorId()).contains(actorId);
    assertThat(caller.delegatedScopes()).containsExactly("access_as_provider");
    assertThat(caller.providerOfficeCodes()).containsExactly("1A001B");
    assertThat(caller.applicationRoles()).containsExactly("LAA_PROVIDER");
  }

  @Test
  void givenTokenWithoutOid_whenCurrentCaller_thenReturnsCallerWithoutActorIdentity() {
    authenticate(Map.of("scp", "access_as_user"));

    assertThat(factory.currentCaller().actorId()).isEmpty();
  }

  @Test
  void givenTokenWithMalformedOid_whenCurrentCaller_thenReturnsCallerWithoutActorIdentity() {
    authenticate(Map.of("oid", "not-a-uuid", "scp", "access_as_user"));

    assertThat(factory.currentCaller().actorId()).isEmpty();
  }

  private void authenticate(Map<String, Object> claims) {
    Jwt jwt =
        new Jwt(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }
}
