package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * DTO for application summary data without the applicationContent JSON blob.
 * Used for efficient querying in list/search operations.
 */
@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class ApplicationSummaryDto {
  private UUID id;
  private ApplicationStatus status;
  private String laaReference;
  private String officeCode;
  private Instant submittedAt;
  private Instant modifiedAt;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private Boolean isAutoGranted;
  private Boolean isLead;
  private UUID caseworkerId;
}

