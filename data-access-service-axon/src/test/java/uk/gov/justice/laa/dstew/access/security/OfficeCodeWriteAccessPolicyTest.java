package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OfficeCodeWriteAccessPolicyTest {

  private final OfficeCodeWriteAccessPolicy policy = new OfficeCodeWriteAccessPolicy();

  @Test
  void givenProviderScopeAndMatchingOfficeCode_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatCode(() -> policy.requireWriteAccess("1A001B", caller)).doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireWriteAccess("2B002C", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenBothScopesAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_user access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireWriteAccess("2B002C", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUserOnlyScope_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_user");

    assertThatCode(() -> policy.requireWriteAccess("2B002C", caller)).doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeWithoutAccounts_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider");

    assertThatThrownBy(() -> policy.requireWriteAccess("1A001B", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUnsupportedScope_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("unrelated_scope");

    assertThatThrownBy(() -> policy.requireWriteAccess("1A001B", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndMissingResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireWriteAccess(null, caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndBlankResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireWriteAccess("  ", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void
      givenProviderScopeAndWhitespacePaddedMatchingOfficeCodes_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_provider", " 1A001B ");

    assertThatCode(() -> policy.requireWriteAccess(" 1A001B ", caller)).doesNotThrowAnyException();
  }

  private AuthenticatedCaller caller(String scopes, String... officeCodes) {
    return new AuthenticatedCaller(
        Optional.empty(), Set.of(scopes.split("\\s+")), Set.of(officeCodes), Set.of());
  }
}
