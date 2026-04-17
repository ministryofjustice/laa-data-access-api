package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullLegalFrameworkMeritsTaskList {
  private String id;
  private String legalAidApplicationId;
  private String serializedData;
  private String createdAt;
  private String updatedAt;
}

