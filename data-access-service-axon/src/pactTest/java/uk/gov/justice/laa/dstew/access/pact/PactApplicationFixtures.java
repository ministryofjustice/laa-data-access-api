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
 * in the shared test utilities, which is known to pass the content schema validation.
 */
final class PactApplicationFixtures {

  /** The application every current consumer state refers to, explicitly or implicitly. */
  static final UUID APPLICATION_001 = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final UUID PROCEEDING_001 =
      UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID SCOPE_LIMITATION_001 =
      UUID.fromString("00000000-0000-0000-0000-00000000b001");

  private PactApplicationFixtures() {}

  /** A submitted special children act application for client Alice Anderson. */
  static ApplicationCreateRequest submittedApplication001() {
    return ApplicationCreateRequest.builder()
        .id(APPLICATION_001)
        .status(ApplicationStatus.APPLICATION_SUBMITTED)
        .laaReference("LAA-REF-0001")
        .applicationContent(application001Content())
        .build();
  }

  private static Map<String, Object> application001Content() {
    return Map.ofEntries(
        Map.entry("createdAt", "2026-01-15T09:30:00Z"),
        Map.entry("submittedAt", "2026-01-15T10:00:00Z"),
        Map.entry(
            "provider", Map.of("officeCode", "1A001B", "contactEmail", "provider@example.com")),
        Map.entry(
            "client",
            Map.ofEntries(
                Map.entry("firstName", "Alice"),
                Map.entry("lastName", "Anderson"),
                Map.entry("dateOfBirth", "1980-01-01"),
                Map.entry("appliedPreviously", false),
                Map.entry("addresses", List.of(homeAddress())))),
        Map.entry("proceedings", List.of(careOrderProceeding())));
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

  private static Map<String, Object> careOrderProceeding() {
    return Map.ofEntries(
        Map.entry("id", PROCEEDING_001.toString()),
        Map.entry("leadProceeding", true),
        Map.entry("code", "SE003"),
        Map.entry("meaning", "Care order"),
        Map.entry("description", "Care order"),
        Map.entry("matterType", "special children act (SCA)"),
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
        Map.entry("scopeLimitations", List.of(finalHearingScopeLimitation())));
  }

  private static Map<String, Object> finalHearingScopeLimitation() {
    return Map.ofEntries(
        Map.entry("id", SCOPE_LIMITATION_001.toString()),
        Map.entry("type", "SUBSTANTIVE"),
        Map.entry("code", "FM062"),
        Map.entry("meaning", "Final hearing"),
        Map.entry("description", "Limited to all steps up to and including the final hearing"));
  }
}
