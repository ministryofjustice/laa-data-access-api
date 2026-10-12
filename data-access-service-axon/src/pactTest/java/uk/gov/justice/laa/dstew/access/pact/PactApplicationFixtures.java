package uk.gov.justice.laa.dstew.access.pact;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

/**
 * Deterministic application content for Pact provider states.
 *
 * <p>Every value is fixed on purpose. State handlers may run more than once against the same
 * in-memory database, and the create command is only idempotent when the request fingerprint
 * matches, so nothing here may be random. The shape mirrors {@code ApplicationCreateRequestFixture}
 * in the shared test utilities, which is known to pass the content schema validation. Identifiers
 * nested in the content (proceeding, scope limitation) are derived from the application ID so two
 * applications never share them.
 */
final class PactApplicationFixtures {

  /** Office code shared by every seeded application, so any pair of them can be linked. */
  static final String OFFICE_CODE = "1A001B";

  private PactApplicationFixtures() {}

  /** The application behind the original Decide states: Alice Anderson, reference LAA-REF-0001. */
  static ApplicationCreateRequest submittedApplication001() {
    return submittedApplication(PactIds.APPLICATION_001, "LAA-REF-0001", "Alice", "Anderson");
  }

  /** A submitted special children act application with the given identity. */
  static ApplicationCreateRequest submittedApplication(
      UUID applicationId, String laaReference, String clientFirstName, String clientLastName) {
    return ApplicationCreateRequest.builder()
        .id(applicationId)
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .laaReference(laaReference)
        .applicationContent(content(applicationId, clientFirstName, clientLastName))
        .build();
  }

  /** Derives a stable proceeding ID from the application ID. */
  static UUID proceedingIdFor(UUID applicationId) {
    return UUID.nameUUIDFromBytes(("proceeding:" + applicationId).getBytes());
  }

  private static Map<String, Object> content(
      UUID applicationId, String clientFirstName, String clientLastName) {
    return Map.ofEntries(
        Map.entry("createdAt", "2026-01-15T09:30:00Z"),
        Map.entry("submittedAt", "2026-01-15T10:00:00Z"),
        Map.entry(
            "provider", Map.of("officeCode", OFFICE_CODE, "contactEmail", "provider@example.com")),
        Map.entry(
            "client",
            Map.ofEntries(
                Map.entry("firstName", clientFirstName),
                Map.entry("lastName", clientLastName),
                Map.entry("dateOfBirth", "1980-01-01"),
                Map.entry("appliedPreviously", false),
                Map.entry("addresses", List.of(homeAddress())))),
        Map.entry("proceedings", List.of(careOrderProceeding(applicationId))));
  }

  private static Map<String, Object> homeAddress() {
    return Map.ofEntries(
        Map.entry("location", "home"),
        Map.entry("addressLineOne", "1 Analytical Engine Way"),
        Map.entry("city", "London"),
        Map.entry("postcode", "SW1A 1AA"),
        Map.entry("countryCode", "GBR"),
        Map.entry("countryName", "United Kingdom"));
  }

  private static Map<String, Object> careOrderProceeding(UUID applicationId) {
    UUID scopeLimitationId =
        UUID.nameUUIDFromBytes(("scope-limitation:" + applicationId).getBytes());
    return Map.ofEntries(
        Map.entry("id", proceedingIdFor(applicationId).toString()),
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
        Map.entry("substantiveLevelOfServiceCode", 3),
        Map.entry("substantiveLevelOfServiceName", "Full Representation"),
        Map.entry("emergencyLevelOfServiceCode", 3),
        Map.entry("emergencyLevelOfServiceName", "Full Representation"),
        Map.entry(
            "scopeLimitations",
            List.of(
                Map.ofEntries(
                    Map.entry("id", scopeLimitationId.toString()),
                    Map.entry("type", "SUBSTANTIVE"),
                    Map.entry("code", "FM062"),
                    Map.entry("meaning", "Final hearing"),
                    Map.entry(
                        "description",
                        "Limited to all steps up to and including the final hearing")))));
  }
}
