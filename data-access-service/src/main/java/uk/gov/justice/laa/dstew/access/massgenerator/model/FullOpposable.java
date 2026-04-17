package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullOpposable {
  private String id;
  private String createdAt;
  private String updatedAt;
  private String firstName;
  private String lastName;
}
