package uk.gov.justice.laa.dstew.access.security;

import static uk.gov.justice.laa.dstew.access.security.ApplicationReadScopeAuthorizationManager.ACCESS_AS_PROVIDER;
import static uk.gov.justice.laa.dstew.access.security.ApplicationReadScopeAuthorizationManager.ACCESS_AS_USER;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Authorizes writes to resources owned by a provider office code. */
@Component
public class OfficeCodeWriteAccessPolicy {

  /** Requires the current caller to be able to write a resource with the supplied office code. */
  public void requireWriteAccess(String resourceOfficeCode) {
    Set<String> scopes = SecurityHelper.getScopes();
    if (scopes.contains(ACCESS_AS_PROVIDER)) {
      requireProviderOfficeAccess(resourceOfficeCode);
      return;
    }
    if (scopes.contains(ACCESS_AS_USER)) {
      return;
    }
    throw new AccessDeniedException("Token does not contain a supported write access scope");
  }

  private void requireProviderOfficeAccess(String resourceOfficeCode) {
    String normalisedResourceOfficeCode = normalise(resourceOfficeCode);
    Set<String> permittedOfficeCodes = new LinkedHashSet<>();
    SecurityHelper.getLaaAccounts().stream()
        .map(this::normalise)
        .filter(Objects::nonNull)
        .forEach(permittedOfficeCodes::add);

    if (normalisedResourceOfficeCode == null
        || !permittedOfficeCodes.contains(normalisedResourceOfficeCode)) {
      throw new AccessDeniedException("Caller is not authorised to write this resource");
    }
  }

  private String normalise(String officeCode) {
    if (officeCode == null) {
      return null;
    }
    String trimmedOfficeCode = officeCode.trim();
    return trimmedOfficeCode.isEmpty() ? null : trimmedOfficeCode;
  }
}
