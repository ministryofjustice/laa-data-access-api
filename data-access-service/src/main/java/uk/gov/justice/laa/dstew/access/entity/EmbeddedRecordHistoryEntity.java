package uk.gov.justice.laa.dstew.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Represents the common audit fields in entities.
 */
@ExcludeFromGeneratedCodeCoverage
@Embeddable
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EmbeddedRecordHistoryEntity {

  @Column(name = "created_at", updatable = false)
  @CreatedDate  //@CreationTimestamp
  private Instant createdAt;

  @Column(name = "modified_at")
  @LastModifiedDate  //@UpdateTimestamp
  private Instant updatedAt;
}