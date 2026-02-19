package uk.gov.justice.laa.dstew.access.projection;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.MatterType;

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
  private UUID caseworker_Id;
  private String individuals_FirstName;
  private String individuals_LastName;
  private LocalDate individuals_DateOfBirth;
}

//package uk.gov.justice.laa.dstew.access.projection;
//
//import java.time.Instant;
//import java.time.LocalDate;
//import java.util.UUID;
//import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
//import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
//import uk.gov.justice.laa.dstew.access.model.MatterType;
//
//public interface ApplicationSummaryResult {
//  UUID getId();
//
//  ApplicationStatus getStatus();
//
//  String getLaaReference();
//
//  String getOfficeCode();
//
//  Instant getSubmittedAt();
//
//  Instant getModifiedAt();
//
//  Boolean getUsedDelegatedFunctions();
//
//  CategoryOfLaw getCategoryOfLaw();
//
//  MatterType getMatterType();
//
//  Boolean getIsAutoGranted();
//
//  // Caseworker FK only (no entity projection)
//  UUID getCaseworker_Id();
//
//  // CLIENT individual fields
//  String getIndividuals_FirstName();
//
//  String getIndividuals_LastName();
//
//  LocalDate getIndividuals_DateOfBirth();
////  UUID getId();
////  ApplicationStatus getStatus();
////  String getLaaReference();
////  String getOfficeCode();
////  Instant getSubmittedAt();
////  Instant getModifiedAt();
////  Boolean getUsedDelegatedFunctions();
////  CategoryOfLaw getCategoryOfLaw();
////  MatterType getMatterType();
////  Boolean getIsAutoGranted();
////  CaseworkerProjection getCaseworker();
//////  Set<IndividualProjection> getIndividuals();
//////  Set<LinkedApplicationProjection> getLinkedApplications();
////
////  interface CaseworkerProjection { UUID getId(); }
//////  interface IndividualProjection {
//////    String getFirstName();
//////    String getLastName();
//////    LocalDate getDateOfBirth();
//////    IndividualType getType();
//////  }
//////  interface LinkedApplicationProjection { UUID getId(); }
//}
