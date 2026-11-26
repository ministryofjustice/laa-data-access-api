package uk.gov.justice.laa.dstew.access.bdd.fixtures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApplicationFixtureFactory {
  public Map<String, Object> draft(UUID applicationId, UUID proceedingId, String officeCode) {
    return Map.of(
        "id",
        applicationId.toString(),
        "status",
        "APPLICATION_SUBMITTED",
        "laaReference",
        "BDD-" + applicationId.toString().substring(0, 8),
        "applicationContent",
        Map.of(
            "createdAt", "2026-01-01T00:00:00Z",
            "submittedAt", "2026-01-01T00:00:00Z",
            "provider", Map.of("officeCode", officeCode, "contactEmail", "bdd@example.com"),
            "client", client(),
            "proceedings", List.of(proceeding(proceedingId))));
  }

  /** Builds a decision request; {@code decision} is {@code GRANTED} or {@code REFUSED}. */
  public Map<String, Object> decision(UUID proceedingId, long applicationVersion, String decision) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("overallDecision", decision);
    request.put(
        "proceedings",
        List.of(
            Map.of(
                "proceedingId", proceedingId.toString(),
                "meritsDecision",
                    Map.of(
                        "decision",
                        decision,
                        "reason",
                        "Decision reason",
                        "justification",
                        "Decision justification"))));
    if ("GRANTED".equals(decision)) request.put("certificate", Map.of("source", "bdd"));
    request.put(
        "eventHistory", Map.of("eventType", "DECISION", "eventDescription", "Decision recorded"));
    request.put("applicationVersion", applicationVersion);
    return request;
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
