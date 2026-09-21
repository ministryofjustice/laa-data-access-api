package uk.gov.justice.laa.dstew.access.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Converts the authenticated Entra OID claim into the authoritative user identifier. */
@Component
public class AuthenticatedUserId {

  /** Returns the current request's Entra OID as a UUID or rejects an unusable identity. */
  public UUID get() {
    String oid =
        SecurityHelper.getEntraOid()
            .orElseThrow(() -> new AccessDeniedException("An Entra OID is required"));
    try {
      return UUID.fromString(oid);
    } catch (IllegalArgumentException exception) {
      throw new AccessDeniedException("The Entra OID must be a UUID", exception);
    }
  }
}
