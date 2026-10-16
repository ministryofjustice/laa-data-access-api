package uk.gov.justice.laa.dstew.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedCallerTest {

  @Test
  void givenTokenFacts_whenCreated_thenNormalisesAndDefensivelyCopiesCollections() {
    UUID actorId = UUID.randomUUID();
    Set<String> scopes = new LinkedHashSet<>(Set.of(" access_as_provider ", ""));
    Set<String> officeCodes = new LinkedHashSet<>(Set.of(" 1A001B ", ""));
    Set<String> applicationRoles = new LinkedHashSet<>(Set.of(" LAA_PROVIDER ", ""));

    AuthenticatedCaller caller =
        new AuthenticatedCaller(Optional.of(actorId), scopes, officeCodes, applicationRoles);
    scopes.add("later_scope");
    officeCodes.add("2B002C");
    applicationRoles.add("LATER_ROLE");

    assertThat(caller.actorId()).contains(actorId);
    assertThat(caller.delegatedScopes()).containsExactly("access_as_provider");
    assertThat(caller.providerOfficeCodes()).containsExactly("1A001B");
    assertThat(caller.applicationRoles()).containsExactly("LAA_PROVIDER");
  }

  @Test
  void givenNullActorId_whenCreated_thenRejectsCaller() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AuthenticatedCaller(null, Set.of(), Set.of(), Set.of()))
        .withMessage("actorId must not be null");
  }

  @Test
  void givenNullAndBlankClaimValues_whenCreated_thenNormalisesToEmptyImmutableSets() {
    Set<String> scopes = new LinkedHashSet<>();
    scopes.add(null);
    scopes.add(" ");

    AuthenticatedCaller caller =
        new AuthenticatedCaller(Optional.empty(), scopes, null, new LinkedHashSet<>(Set.of(" ")));

    assertThat(caller.delegatedScopes()).isEmpty();
    assertThat(caller.providerOfficeCodes()).isEmpty();
    assertThat(caller.applicationRoles()).isEmpty();
  }
}
