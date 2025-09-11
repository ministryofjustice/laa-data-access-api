package uk.gov.justice.laa.dstew.access.shared.security;

/**
 * Check if current request has authorization to act in a specific app role.
 * Interface to be implemented by the `entra` Spring component.
 */
public interface EffectiveAuthorizationProvider {
  /**
   * Check if the access token's app `roles` claim includes a given name.
   *
   * @param name String app role name to look for.
   * @return boolean true if it was found.
   */
  boolean hasAppRole(String name);

  /**
   * Check if the access token's app `roles` claim contains any of the given names.
   *
   * @param names String app role names to look for.
   * @return boolean true if any was found.
   */
  boolean hasAnyAppRole(String... names);
}

