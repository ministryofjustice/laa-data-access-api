package uk.gov.justice.laa.dstew.access.massgenerator.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullApplicant {
  private String id;
  private String firstName;
  private String lastName;
  private String dateOfBirth;
  private String createdAt;
  private String updatedAt;
  private String email;
  private String nationalInsuranceNumber;
  private Boolean employed;
  private Boolean selfEmployed;
  private Boolean armedForces;
  private Boolean hasNationalInsuranceNumber;
  private Integer ageForMeansTestPurposes;
  private Boolean hasPartner;
  private Boolean receivesStateBenefits;
  private Boolean partnerHasContraryInterest;
  private Boolean studentFinance;
  private String studentFinanceAmount;
  private Boolean extraEmploymentInformation;
  private String extraEmploymentInformationDetails;
  private String lastNameAtBirth;
  private Boolean changedLastName;
  private Boolean sameCorrespondenceAndHomeAddress;
  private Boolean noFixedResidence;
  private String correspondenceAddressChoice;
  private Boolean sharedBenefitWithPartner;
  private Boolean appliedPreviously;
  private String previousReference;
  private String relationshipToChildren;
  private List<FullApplicantAddress> addresses;
}
