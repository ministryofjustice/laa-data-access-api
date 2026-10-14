package uk.gov.justice.laa.dstew.access.security;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, minimum-necessary projection of the authenticated caller's validated token claims. */
public record AuthenticatedCaller(
    Optional<java.util.UUID> actorId,
    Set<String> delegatedScopes,
    Set<String> providerOfficeCodes,
    Set<String> applicationRoles) {

  /** Normalises claim values and defensively copies caller facts for command transport. */
  public AuthenticatedCaller {
    actorId = Objects.requireNonNull(actorId, "actorId must not be null");
    delegatedScopes = normalise(delegatedScopes);
    providerOfficeCodes = normalise(providerOfficeCodes);
    applicationRoles = normalise(applicationRoles);
  }

  private static Set<String> normalise(Set<String> values) {
    if (values == null) {
      return Set.of();
    }
    Set<String> normalised = new LinkedHashSet<>();
    values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .forEach(normalised::add);
    return Set.copyOf(normalised);
  }
}
