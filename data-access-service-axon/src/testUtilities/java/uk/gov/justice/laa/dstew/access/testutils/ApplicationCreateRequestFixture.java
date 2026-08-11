package uk.gov.justice.laa.dstew.access.testutils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/** Builds valid API requests shared by fast and Postgres integration tests. */
public final class ApplicationCreateRequestFixture {

  private ApplicationCreateRequestFixture() {}

  /** Creates valid application content using the supplied identifiers. */
  public static Map<String, Object> validApplicationContent(
      UUID applicationId, UUID applyProceedingId) {
    return Map.ofEntries(
        Map.entry("id", applicationId.toString()),
        Map.entry("createdAt", "2026-07-14T12:00:00Z"),
        Map.entry("submittedAt", "2026-07-14T12:30:00Z"),
        Map.entry(
            "provider", Map.of("officeCode", "1A001B", "contactEmail", "provider@example.com")),
        Map.entry(
            "client",
            Map.ofEntries(
                Map.entry("firstName", "Ada"),
                Map.entry("lastName", "Lovelace"),
                Map.entry("dateOfBirth", "1815-12-10"),
                Map.entry("appliedPreviously", false),
                Map.entry("addresses", List.of(validAddressContent())))),
        Map.entry("proceedings", List.of(validProceedingContent(applyProceedingId))));
  }

  /** Creates valid proceeding content using the supplied identifier. */
  public static Map<String, Object> validProceedingContent(UUID proceedingId) {
    return Map.ofEntries(
        Map.entry("id", proceedingId.toString()),
        Map.entry("leadProceeding", true),
        Map.entry("code", "SE003"),
        Map.entry("meaning", "Care order"),
        Map.entry("description", "Care order"),
        Map.entry("matterType", "SPECIAL_CHILDREN_ACT"),
        Map.entry("matterTypeCode", "KPBLW"),
        Map.entry("categoryOfLaw", "Family"),
        Map.entry("categoryOfLawCode", "MAT"),
        Map.entry("clientInvolvementType", "A"),
        Map.entry("usedDelegatedFunctions", false),
        Map.entry("delegatedFunctionsCostLimitation", 0),
        Map.entry("substantiveCostLimitation", 2500),
        Map.entry("substantiveLevelOfService", 3),
        Map.entry("substantiveLevelOfServiceName", "Full Representation"),
        Map.entry("emergencyLevelOfService", 3),
        Map.entry("emergencyLevelOfServiceName", "Full Representation"),
        Map.entry("scopeLimitations", List.of(validScopeLimitationContent())));
  }

  /** Creates a valid request using the supplied identifiers. */
  public static ApplicationCreateRequest validCreateApplicationRequest(
      UUID applicationId, UUID applyProceedingId) {
    return ApplicationCreateRequest.builder()
        .id(applicationId)
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(validApplicationContent(applicationId, applyProceedingId))
        .laaReference("LAA-123")
        .build();
  }

  /** Creates a valid request that links the new Application to an existing Application. */
  public static ApplicationCreateRequest validLinkedCreateApplicationRequest(
      UUID applicationId, UUID applyProceedingId, UUID leadApplicationId) {
    return validLinkedCreateApplicationRequest(
        applicationId, applyProceedingId, leadApplicationId, applicationId);
  }

  /** Creates a valid request with an explicitly declared linked Application group member. */
  public static ApplicationCreateRequest validLinkedCreateApplicationRequest(
      UUID applicationId,
      UUID applyProceedingId,
      UUID leadApplicationId,
      UUID associatedApplicationId) {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, applyProceedingId);
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    content.put(
        "allLinkedApplications",
        List.of(
            Map.ofEntries(
                Map.entry("id", UUID.randomUUID().toString()),
                Map.entry("leadApplicationId", leadApplicationId.toString()),
                Map.entry("associatedApplicationId", associatedApplicationId.toString()),
                Map.entry("targetApplicationId", applicationId.toString()),
                Map.entry("linkTypeCode", "ASSOCIATED"),
                Map.entry("createdAt", "2026-07-14T12:00:00Z"),
                Map.entry("updatedAt", "2026-07-14T12:30:00Z"),
                Map.entry("confirmLink", true))));
    return ApplicationCreateRequest.builder()
        .id(applicationId)
        .status(request.getStatus())
        .applicationContent(content)
        .laaReference(request.getLaaReference())
        .build();
  }

  private static Map<String, Object> validAddressContent() {
    return Map.ofEntries(
        Map.entry("location", "home"),
        Map.entry("addressLineOne", "1 Analytical Engine Way"),
        Map.entry("city", "London"),
        Map.entry("postcode", "SW1A 1AA"),
        Map.entry("countryCode", "GBR"),
        Map.entry("countryName", "United Kingdom"));
  }

  private static Map<String, Object> validScopeLimitationContent() {
    return Map.ofEntries(
        Map.entry("id", UUID.randomUUID().toString()),
        Map.entry("type", "SUBSTANTIVE"),
        Map.entry("code", "FM062"),
        Map.entry("meaning", "Final hearing"),
        Map.entry("description", "Limited to all steps up to and including the final hearing"));
  }
}
