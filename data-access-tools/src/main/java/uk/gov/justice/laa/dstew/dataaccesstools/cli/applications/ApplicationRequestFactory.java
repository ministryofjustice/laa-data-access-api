package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ApplicationRequestFactory {
  public ApplicationData create() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    String reference = "LAA-CLI-" + applicationId.toString().substring(0, 8).toUpperCase();
    String timestamp = Instant.now().toString();
    String request =
        """
        {"id":"%s","status":"APPLICATION_SUBMITTED","laaReference":"%s","applicationContent":{
          "createdAt":"%s","submittedAt":"%s",
          "provider":{"officeCode":"1A001B","contactEmail":"civil.provider@example.com"},
          "client":{"firstName":"Ada","lastName":"Lovelace","dateOfBirth":"%s","appliedPreviously":false,
            "addresses":[{"location":"home","addressLineOne":"1 Analytical Engine Way","city":"London","postcode":"SW1A 1AA","countryCode":"GBR","countryName":"United Kingdom"}]},
          "proceedings":[{"id":"%s","leadProceeding":true,"code":"SE003","meaning":"Care order","description":"Care order","matterType":"SPECIAL_CHILDREN_ACT","matterTypeCode":"KPBLW","categoryOfLaw":"Family","categoryOfLawCode":"MAT","clientInvolvementType":"Respondent","clientInvolvementTypeCode":"A","usedDelegatedFunctions":false,"delegatedFunctionsCostLimitation":"0","substantiveCostLimitation":"2500","substantiveLevelOfService":3,"substantiveLevelOfServiceName":"Full Representation","emergencyLevelOfService":3,"emergencyLevelOfServiceName":"Full Representation","scopeLimitations":[{"id":"%s","type":"SUBSTANTIVE","code":"FM062","meaning":"Final hearing","description":"Limited to all steps up to and including the final hearing"}]}]
        }}
        """
            .formatted(
                applicationId,
                reference,
                timestamp,
                timestamp,
                LocalDate.now().minusYears(35),
                proceedingId,
                UUID.randomUUID());
    return new ApplicationData(applicationId, proceedingId, reference, request);
  }

  public record ApplicationData(
      UUID applicationId, UUID proceedingId, String laaReference, String request) {}
}
