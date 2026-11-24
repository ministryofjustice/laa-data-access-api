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
        "Interpreter costs for proceedings",
        "disbursementDetails",
        Map.of("disbursementPurpose", "Interpreter", "disbursementAmount", 150.25));
  }

  public Map<String, Object> updatedDraft(String justification) {
    return Map.of(
        "justification",
        justification,
        "disbursementDetails",
        Map.of("disbursementPurpose", "Interpreter", "disbursementAmount", 175.50));
  }

  /** Builds a decision request; {@code decision} is {@code GRANTED} or {@code REFUSED}. */
  public Map<String, Object> decision(String decision, long priorAuthorityVersion) {
    return Map.of(
        "decision",
        decision,
        "decisionJustification",
        "Decision justification",
        "amountGranted",
        150.25,
        "dateGranted",
        Instant.now().toString(),
        "eventHistory",
        Map.of("eventType", "DECISION", "eventDescription", "Decision recorded"),
        "priorAuthorityVersion",
        priorAuthorityVersion);
  }
}
