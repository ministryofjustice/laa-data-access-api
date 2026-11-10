package uk.gov.justice.laa.dstew.access.bdd.fixtures;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApplicationFixtureFactory {
  public Map<String, Object> draft(UUID applicationId, UUID proceedingId) {
    return Map.of(
        "id",
        applicationId.toString(),
        "status",
        "APPLICATION_IN_PROGRESS",
        "laaReference",
        "BDD-" + applicationId.toString().substring(0, 8),
        "applicationContent",
        Map.of(
            "createdAt", "2026-01-01T00:00:00Z",
            "provider", Map.of("officeCode", "1A001B", "contactEmail", "bdd@example.com"),
            "client", client(),
            "proceedings", List.of(proceeding(proceedingId))));
  }

  public Map<String, Object> grantDecision(UUID proceedingId, long applicationVersion) {
    return Map.of(
        "overallDecision",
        "GRANTED",
        "proceedings",
        List.of(
            Map.of(
                "proceedingId", proceedingId.toString(),
                "meritsDecision",
                    Map.of(
                        "decision",
                        "GRANTED",
                        "reason",
                        "BDD smoke",
                        "justification",
                        "BDD smoke"))),
        "certificate",
        Map.of("source", "bdd"),
        "applicationVersion",
        applicationVersion);
  }

  private Map<String, Object> client() {
    return Map.ofEntries(
        Map.entry("firstName", "Ada"),
        Map.entry("lastName", "Lovelace"),
        Map.entry("dateOfBirth", "1815-12-10"),
        Map.entry("appliedPreviously", false),
        Map.entry(
            "addresses",
            List.of(
                Map.of(
                    "location",
                    "home",
                    "addressLineOne",
                    "1 BDD Way",
                    "city",
                    "London",
                    "postcode",
                    "SW1A 1AA",
                    "countryCode",
                    "GBR",
                    "countryName",
                    "United Kingdom"))));
  }

  private Map<String, Object> proceeding(UUID proceedingId) {
    return Map.ofEntries(
        Map.entry("id", proceedingId.toString()),
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
        Map.entry("substantiveLevelOfServiceCode", 3),
        Map.entry("substantiveLevelOfServiceName", "Full Representation"),
        Map.entry("emergencyLevelOfServiceCode", 3),
        Map.entry("emergencyLevelOfServiceName", "Full Representation"),
        Map.entry(
            "scopeLimitations",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "type",
                    "SUBSTANTIVE",
                    "code",
                    "FM062",
                    "meaning",
                    "Final hearing",
                    "description",
                    "Limited"))));
  }
}
