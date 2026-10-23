package uk.gov.justice.laa.dstew.access.security;

import java.util.Objects;
import java.util.UUID;

/** A command-side resource protected by provider-office write access. */
public record OfficeCodeWriteAccessTarget(String resourceType, UUID resourceId, String officeCode)
    implements WriteAccessTarget {
  /** Validates the required typed target attributes. */
  public OfficeCodeWriteAccessTarget {
    resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
    resourceId = Objects.requireNonNull(resourceId, "resourceId must not be null");
    officeCode = Objects.requireNonNull(officeCode, "officeCode must not be null");
  }
}
