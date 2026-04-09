package uk.gov.justice.laa.dstew.access.entity;

import java.time.Instant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

/**
 * Represents a summary of an application.
 * This class is designed to hold a subset of application data that is relevant for summary views.
 * It includes basic information about the application, its status, and related individual details.
 */
@ExcludeFromGeneratedCodeCoverage
@Builder(toBuilder = true)
@Getter
@AllArgsConstructor
public class ApplicationSummaryResult {
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
  private String individualsFirstName;
  private String individualsLastName;
  private LocalDate individualsDateOfBirth;
}

