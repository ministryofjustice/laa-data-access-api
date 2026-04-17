package uk.gov.justice.laa.dstew.access.massgenerator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FullApplicantAddress {
  private String id;
  private String addressLineOne;
  private String addressLineTwo;
  private String addressLineThree;
  private String city;
  private String county;
  private String postcode;
  private String applicantId;
  private String createdAt;
  private String updatedAt;
  private String organisation;
  private Boolean lookupUsed;
  private String lookupId;
  private String buildingNumberName;
  private String location;
  private String countryCode;
  private String countryName;
  private String careOf;
  private String careOfFirstName;
  private String careOfLastName;
  private String careOfOrganisationName;
}
