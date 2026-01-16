package uk.gov.justice.laa.dstew.access.entity;

import java.time.Instant;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Interface for entities that are auditable. Helps with mapping the created and updated timestamps
 * and user information.
 */
@ExcludeFromGeneratedCodeCoverage
public interface AuditableEntity {
  Instant getCreatedAt();

  String getCreatedBy();

  Instant getUpdatedAt();

  String getUpdatedBy();
}
