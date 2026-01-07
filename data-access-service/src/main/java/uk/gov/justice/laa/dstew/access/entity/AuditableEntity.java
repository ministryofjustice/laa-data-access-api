package uk.gov.justice.laa.dstew.access.entity;

import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

import java.time.Instant;

/**
 * Interface for entities that are auditable.
 * Helps with mapping the created and updated timestamps and user information.
 */
@ExcludeFromGeneratedCodeCoverage
public interface AuditableEntity {
  Instant getCreatedAt();

  String getCreatedBy();

  Instant getUpdatedAt();

  String getUpdatedBy();
}
