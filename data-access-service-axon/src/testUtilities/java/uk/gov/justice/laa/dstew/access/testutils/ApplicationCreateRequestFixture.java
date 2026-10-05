package uk.gov.justice.laa.dstew.access.testutils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.datafaker.Faker;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/** Builds valid API requests shared by fast and Postgres integration tests. */
public final class ApplicationCreateRequestFixture {

  private ApplicationCreateRequestFixture() {}

  /** Creates valid application content using the supplied identifiers. */
  public static Map<String, Object> validApplicationContent(
      UUID applicationId, UUID applyProceedingId) {
    return Map.ofEntries(
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
                Map.entry("addresses", List.of(validAddressContentRandom())))),
        Map.entry("proceedings", List.of(validProceedingContent(applyProceedingId))));
  }

  /** Creates valid application content using the supplied identifiers. */
  public static Map<String, Object> validApplicationContentWithRandomData(
      UUID applicationId, UUID applyProceedingId) {
    Faker faker = new Faker();
    return Map.ofEntries(
        Map.entry("createdAt", "2026-07-14T12:00:00Z"),
        Map.entry("submittedAt", "2026-07-14T12:30:00Z"),
        Map.entry(
            "provider",
            Map.of("officeCode", "1A001B", "contactEmail", faker.internet().emailAddress())),
        Map.entry(
            "client",
            Map.ofEntries(
                Map.entry("firstName", faker.name().firstName()),
                Map.entry("lastName", faker.name().lastName()),
                Map.entry("dateOfBirth", faker.timeAndDate().birthday(1, 99)),
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
        Map.entry("clientInvolvementType", "Respondent"),
        Map.entry("clientInvolvementTypeCode", "A"),
        Map.entry("usedDelegatedFunctions", false),
        Map.entry("delegatedFunctionsCostLimitation", "0"),
        Map.entry("substantiveCostLimitation", "2500"),
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

  /** Creates a valid request using the supplied Apply identifiers. */
  public static ApplicationCreateRequest validCreateApplicationRequestWithRandomData(
      UUID applicationId, UUID applyProceedingId) {

    return ApplicationCreateRequest.builder()
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(validApplicationContentWithRandomData(applicationId, applyProceedingId))
        .laaReference("LAA-123")
        .build();
  }

  /** Creates a valid request with potentialDuplicates using the supplied identifiers. */
  public static ApplicationCreateRequest validCreateApplicationRequestWithPotentialDuplicates(
      UUID applicationId, UUID applyProceedingId, List<PotentialDuplicate> potentialDuplicates) {
    return ApplicationCreateRequest.builder()
        .id(applicationId)
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .applicationContent(validApplicationContent(applicationId, applyProceedingId))
        .laaReference("LAA-123")
        .potentialDuplicates(potentialDuplicates)
        .build();
  }

  /** Creates a PotentialDuplicate for testing. */
  public static PotentialDuplicate potentialDuplicateWithLaaReference(String laaReference) {
    return PotentialDuplicate.builder()
        .applicationId(UUID.randomUUID())
        .laaReference(laaReference)
        .legacyReference(null)
        .build();
  }

  /** Creates a PotentialDuplicate with both laaReference and legacyReference. */
  public static PotentialDuplicate potentialDuplicateWithBothReferences(
      String laaReference, String legacyReference) {
    return PotentialDuplicate.builder()
        .applicationId(UUID.randomUUID())
        .laaReference(laaReference)
        .legacyReference(legacyReference)
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

  private static Map<String, Object> validAddressContentRandom() {
    Faker faker = new Faker();
    return Map.ofEntries(
        Map.entry("location", "home"),
        Map.entry("addressLineOne", faker.address().streetAddress()),
        Map.entry("city", faker.address().city()),
        Map.entry("postcode", faker.address().postcode()),
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
