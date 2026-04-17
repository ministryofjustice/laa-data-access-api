package uk.gov.justice.laa.dstew.access.usecase.shared.security;

/** Port through which use-case security enforcement is delegated to infrastructure. */
public interface AccessPolicy {
  /**
   * Enforce the access rules expressed by the given annotation.
   *
   * @throws org.springframework.security.access.AccessDeniedException if access is denied
   */
  void enforce(EnforceRole annotation);
}
