package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullOpponentDetails {
  private String id;
  private String legalAidApplicationId;
  private String createdAt;
  private String updatedAt;
  private String ccmsOpponentId;
  private String opposableType;
  private String opposableId;
  private Boolean existsInCCMS;
  private FullOpposable opposable;
}

