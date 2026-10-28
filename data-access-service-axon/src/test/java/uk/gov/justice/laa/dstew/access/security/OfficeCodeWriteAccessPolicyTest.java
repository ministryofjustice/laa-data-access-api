package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OfficeCodeWriteAccessPolicyTest {

  private final OfficeCodeWriteAccessPolicy policy = new OfficeCodeWriteAccessPolicy();

  @Test
  void givenProviderScopeAndMatchingOfficeCode_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatCode(() -> policy.requireOfficeCodeWriteAccess("1A001B", caller))
        .doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess("2B002C", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenBothScopesAndNonMatchingOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_user access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess("2B002C", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUserOnlyScope_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_user");

    assertThatCode(() -> policy.requireOfficeCodeWriteAccess("2B002C", caller))
        .doesNotThrowAnyException();
  }

  @Test
  void givenProviderScopeWithoutAccounts_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess("1A001B", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUnsupportedScope_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("unrelated_scope");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess("1A001B", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndMissingResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess(null, caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenProviderScopeAndBlankResourceOfficeCode_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(() -> policy.requireOfficeCodeWriteAccess("  ", caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void
      givenProviderScopeAndWhitespacePaddedMatchingOfficeCodes_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_provider", " 1A001B ");

    assertThatCode(() -> policy.requireOfficeCodeWriteAccess(" 1A001B ", caller))
        .doesNotThrowAnyException();
  }

  @Test
  void givenProviderAccessToEveryProtectedResource_whenRequireWriteAccess_thenAllows() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B", "2B002C");

    assertThatCode(
            () ->
                policy.requireWriteAccess(
                    List.of(new ProtectedResource("1A001B"), new ProtectedResource("2B002C")),
                    caller))
        .doesNotThrowAnyException();
  }

  @Test
  void givenInaccessibleProtectedResource_whenRequireWriteAccess_thenDenies() {
    AuthenticatedCaller caller = caller("access_as_provider", "1A001B");

    assertThatThrownBy(
            () ->
                policy.requireWriteAccess(
                    List.of(new ProtectedResource("1A001B"), new ProtectedResource("2B002C")),
                    caller))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void givenUserScopeAndProtectedResources_whenRequireWriteAccess_thenAllows() {
    assertThatCode(
            () ->
                policy.requireWriteAccess(
                    List.of(new ProtectedResource("1A001B"), new ProtectedResource("2B002C")),
                    caller("access_as_user")))
        .doesNotThrowAnyException();
  }

  @Test
  void givenNoProtectedResources_whenRequireWriteAccess_thenRejectsResolution() {
    assertThatThrownBy(() -> policy.requireWriteAccess(List.of(), caller("access_as_user")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least one resource");
  }

  private AuthenticatedCaller caller(String scopes, String... officeCodes) {
    return new AuthenticatedCaller(
        Optional.empty(), Set.of(scopes.split("\\s+")), Set.of(officeCodes), Set.of());
  }
}
