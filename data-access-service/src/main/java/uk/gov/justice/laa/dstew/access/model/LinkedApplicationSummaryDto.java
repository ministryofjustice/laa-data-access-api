package uk.gov.justice.laa.dstew.access.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Model to represent linked application summary.
 *
 */
@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@ExcludeFromGeneratedCodeCoverage
public class LinkedApplicationSummaryDto {
  private UUID applicationId;
  private String laaReference;
  private Boolean isLead;
  private UUID leadApplicationId;
}
