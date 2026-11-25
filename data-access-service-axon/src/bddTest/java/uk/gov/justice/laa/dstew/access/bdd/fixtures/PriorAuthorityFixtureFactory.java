package uk.gov.justice.laa.dstew.access.bdd.fixtures;

import java.time.Instant;
import java.util.LinkedHashMap;
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

  /**
   * Builds a decision request; {@code decision} is {@code GRANTED} or {@code REFUSED}. The amount
   * and date are only sent for {@code GRANTED}.
   */
  public Map<String, Object> decision(String decision, long priorAuthorityVersion) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("decision", decision);
    request.put("decisionJustification", "Decision justification");
    if ("GRANTED".equals(decision)) {
      request.put("amountGranted", 150.25);
      request.put("dateGranted", Instant.now().toString());
    }
    request.put(
        "eventHistory", Map.of("eventType", "DECISION", "eventDescription", "Decision recorded"));
    request.put("priorAuthorityVersion", priorAuthorityVersion);
    return request;
  }
}
