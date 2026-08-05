package uk.gov.justice.laa.dstew.access.testutils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;

/** Builds valid API requests shared by fast and Postgres integration tests. */
public final class ApplicationCreateRequestFixture {

  private ApplicationCreateRequestFixture() {}

  /** Creates a valid request using the supplied Apply identifiers. */
  public static ApplicationCreateRequest validCreateApplicationRequest(
      UUID applyApplicationId, UUID applyProceedingId) {
    Map<String, Object> content =
        Map.ofEntries(
            Map.entry("id", applyApplicationId.toString()),
            Map.entry("createdAt", "2026-07-14T12:00:00Z"),
            Map.entry("submittedAt", "2026-07-14T12:30:00Z"),
            Map.entry("office", Map.of("code", "1A001B")),
            Map.entry(
                "provider", Map.of("officeCode", "1A001B", "contactEmail", "provider@example.com")),
            Map.entry(
                "applicant",
                Map.of(
                    "id", UUID.randomUUID().toString(),
                    "appliedPreviously", false,
                    "addresses", List.of(Map.of("id", UUID.randomUUID().toString())))),
            Map.entry(
                "client",
                Map.ofEntries(
                    Map.entry("firstName", "Ada"),
                    Map.entry("lastName", "Lovelace"),
                    Map.entry("dateOfBirth", "1815-12-10"),
                    Map.entry("appliedPreviously", false),
                    Map.entry(
                        "correspondenceAddress",
                        List.of(
                            Map.ofEntries(
                                Map.entry("location", "HOME"),
                                Map.entry("addressLineOne", "1 Analytical Engine Way"),
                                Map.entry("city", "London"),
                                Map.entry("postcode", "SW1A 1AA")))))),
            Map.entry("allLinkedApplications", List.of()),
            Map.entry(
                "proceedings",
                List.of(
                    Map.ofEntries(
                        Map.entry("id", applyProceedingId.toString()),
                        Map.entry("leadProceeding", true),
                        Map.entry("ccmsCode", "SE003"),
                        Map.entry("meaning", "Care order"),
                        Map.entry("description", "Care order"),
                        Map.entry("matterType", "SPECIAL_CHILDREN_ACT"),
                        Map.entry("matterTypeEnum", "SPECIAL_CHILDREN_ACT"),
                        Map.entry("categoryOfLaw", "FAMILY"),
                        Map.entry("categoryOfLawEnum", "FAMILY"),
                        Map.entry("clientInvolvementType", "A"),
                        Map.entry("usedDelegatedFunctions", false),
                        Map.entry("delegatedFunctionsCostLimitation", 0),
                        Map.entry("substantiveCostLimitation", 2500),
                        Map.entry("levelOfService", "FULL"),
                        Map.entry(
                            "scopeLimitations",
                            List.of(
                                Map.of(
                                    "id", UUID.randomUUID().toString(),
                                    "scopeType", "LIMITATION",
                                    "scopeLimitation", "CV117",
                                    "scopeDescription", "Final hearing")))))));

    IndividualCreateRequest individual =
        IndividualCreateRequest.builder()
            .firstName("Ada")
            .lastName("Lovelace")
            .dateOfBirth(LocalDate.of(1815, 12, 10))
            .details(Map.of("preferredName", "Ada"))
            .type(IndividualType.CLIENT)
            .build();

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(content)
        .laaReference("LAA-123")
        .individuals(List.of(individual))
        .build();
  }

  /** Creates a valid request that links the new Application to an existing Apply Application. */
  public static ApplicationCreateRequest validLinkedCreateApplicationRequest(
      UUID applyApplicationId, UUID applyProceedingId, UUID leadApplyApplicationId) {
    return validLinkedCreateApplicationRequest(
        applyApplicationId, applyProceedingId, leadApplyApplicationId, applyApplicationId);
  }

  /** Creates a valid request with an explicitly declared linked Application group member. */
  public static ApplicationCreateRequest validLinkedCreateApplicationRequest(
      UUID applyApplicationId,
      UUID applyProceedingId,
      UUID leadApplyApplicationId,
      UUID associatedApplyApplicationId) {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    content.put(
        "allLinkedApplications",
        List.of(
            Map.ofEntries(
                Map.entry("id", UUID.randomUUID().toString()),
                Map.entry("leadApplicationId", leadApplyApplicationId.toString()),
                Map.entry("associatedApplicationId", associatedApplyApplicationId.toString()),
                Map.entry("targetApplicationId", applyApplicationId.toString()),
                Map.entry("linkTypeCode", "ASSOCIATED"),
                Map.entry("createdAt", "2026-07-14T12:00:00Z"),
                Map.entry("updatedAt", "2026-07-14T12:30:00Z"),
                Map.entry("confirmLink", true))));
    return ApplicationCreateRequest.builder()
        .status(request.getStatus())
        .applicationContent(content)
        .laaReference(request.getLaaReference())
        .individuals(request.getIndividuals())
        .build();
  }
}
