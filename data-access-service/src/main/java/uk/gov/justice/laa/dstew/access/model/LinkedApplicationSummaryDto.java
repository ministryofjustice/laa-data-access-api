package uk.gov.justice.laa.dstew.access.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Represents a slice of information about linked applications.
 */
@ExcludeFromGeneratedCodeCoverage
public interface LinkedApplicationSummaryDto {
  UUID getLeadApplicationId();

  UUID getAssociateApplicationId();

  String getLaaReference();
  
  boolean getIsLead();
}
