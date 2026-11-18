package uk.gov.justice.laa.dstew.access.bdd.fixtures;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PriorAuthorityFixtureFactory {
  public Map<String, Object> draft(UUID applicationId) {
    return Map.of(
        "applicationId",
        applicationId.toString(),
        "priorAuthorityType",
        "DISBURSEMENT",
        "justification",
        "BDD smoke",
        "disbursementDetails",
        Map.of("disbursementPurpose", "Interpreter", "disbursementAmount", 150.25));
  }

  public Map<String, Object> grantDecision(long priorAuthorityVersion) {
    return Map.of(
        "decision",
        "GRANTED",
        "decisionJustification",
        "BDD smoke",
        "amountGranted",
        150.25,
        "dateGranted",
        Instant.now().toString(),
        "eventHistory",
        Map.of("eventType", "DECISION", "eventDescription", "BDD smoke"),
        "priorAuthorityVersion",
        priorAuthorityVersion);
  }
}
