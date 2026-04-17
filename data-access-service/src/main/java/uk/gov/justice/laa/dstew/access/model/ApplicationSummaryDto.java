package uk.gov.justice.laa.dstew.access.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DTO for application summary data projected directly from the database query. */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSummaryDto {
  private UUID id;
  private ApplicationStatus status;
  private String laaReference;
  private String officeCode;
  private Instant createdAt;
  private Instant modifiedAt;
  private Instant submittedAt;
  private Boolean usedDelegatedFunctions;
  private CategoryOfLaw categoryOfLaw;
  private MatterType matterType;
  private Boolean isAutoGranted;
  private UUID caseworkerId;
  private String clientFirstName;
  private String clientLastName;
  private LocalDate clientDateOfBirth;
  private boolean isLead;
}
