package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityHelperTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenJwtAuthenticationWithAccounts_whenRequested_thenReturnsAccountCodes() {
    Jwt jwt = jwt().claim("LAA_ACCOUNTS", List.of("ABC123", "DEF456")).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(SecurityHelper.getLaaAccounts()).containsExactly("ABC123", "DEF456");
  }

  @Test
  void givenNoJwtAuthentication_whenRequested_thenReturnsEmptyList() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "credentials"));

    assertThat(SecurityHelper.getLaaAccounts()).isEmpty();
  }

  @Test
  void givenJwtAuthenticationWithScopes_whenRequested_thenReturnsDistinctScopes() {
    Jwt jwt = jwt().claim("scp", "access_as_user  access_as_provider access_as_user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(SecurityHelper.getScopes())
        .containsExactlyInAnyOrder("access_as_user", "access_as_provider");
  }

  @Test
  void givenJwtAuthenticationWithLeadingScopeWhitespace_whenRequested_thenExcludesBlankScope() {
    Jwt jwt = jwt().claim("scp", " access_as_user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(SecurityHelper.getScopes()).containsExactly("access_as_user");
  }

  @Test
  void givenJwtAuthenticationWithoutScopes_whenRequested_thenReturnsEmptySet() {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt().build()));

    assertThat(SecurityHelper.getScopes()).isEmpty();
  }

  @Test
  void givenNoJwtAuthentication_whenScopesRequested_thenReturnsEmptySet() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "credentials"));

    assertThat(SecurityHelper.getScopes()).isEmpty();
  }

  @Test
  void givenJwtAuthenticationWithOid_whenRequested_thenReturnsEntraOid() {
    Jwt jwt = jwt().claim("oid", "entra-object-id").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    assertThat(SecurityHelper.getEntraOid()).contains("entra-object-id");
    assertThat(SecurityHelper.getEntraOid(jwt)).contains("entra-object-id");
  }

  @Test
  void givenJwtWithoutOid_whenRequested_thenReturnsEmpty() {
    assertThat(SecurityHelper.getEntraOid(jwt().build())).isEqualTo(Optional.empty());
  }

  @Test
  void givenJwtWithBlankOid_whenRequested_thenReturnsEmpty() {
    assertThat(SecurityHelper.getEntraOid(jwt().claim("oid", " ").build()))
        .isEqualTo(Optional.empty());
  }

  private Jwt.Builder jwt() {
    Instant issuedAt = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user")
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plusSeconds(300));
  }
}
