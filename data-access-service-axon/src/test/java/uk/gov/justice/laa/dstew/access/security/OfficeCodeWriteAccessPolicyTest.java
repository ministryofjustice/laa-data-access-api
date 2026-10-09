package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OfficeCodeWriteAccessPolicyTest {

  private final OfficeCodeWriteAccessPolicy policy = new OfficeCodeWriteAccessPolicy();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void givenProviderScopeAndMatchingOfficeCode_whenRequireWriteAccess_thenAllows() {
    authenticate("access_as_provider", List.of("1A001B"));

    assertThatCode(() -> policy.requireWriteAccess("1A001B")).doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    authenticate("access_as_provider", List.of("1A001B"));

    assertThatThrownBy(() -> policy.requireWriteAccess("2B002C"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenBothScopesAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    authenticate("access_as_user access_as_provider", List.of("1A001B"));

    assertThatThrownBy(() -> policy.requireWriteAccess("2B002C"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUserOnlyScope_whenRequireWriteAccess_thenAllows() {
    authenticate("access_as_user", List.of());

    assertThatCode(() -> policy.requireWriteAccess("2B002C")).doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeWithoutAccounts_whenRequireWriteAccess_thenDenies() {
    authenticate("access_as_provider", List.of());

    assertThatThrownBy(() -> policy.requireWriteAccess("1A001B"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUnsupportedScope_whenRequireWriteAccess_thenDenies() {
    authenticate("unrelated_scope", List.of());

    assertThatThrownBy(() -> policy.requireWriteAccess("1A001B"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndMissingResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    authenticate("access_as_provider", List.of("1A001B"));

    assertThatThrownBy(() -> policy.requireWriteAccess(null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndBlankResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    authenticate("access_as_provider", List.of("1A001B"));

    assertThatThrownBy(() -> policy.requireWriteAccess("  "))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void
      givenProviderScopeAndWhitespacePaddedMatchingOfficeCodes_whenRequireWriteAccess_thenAllows() {
    authenticate("access_as_provider", List.of(" 1A001B "));

    assertThatCode(() -> policy.requireWriteAccess(" 1A001B ")).doesNotThrowAnyException();
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
}
