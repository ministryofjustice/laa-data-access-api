package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.List;
import java.util.UUID;

/** Query-specific immutable record for one prior-authority submission and its events. */
public record PriorAuthorityHistoryGroupResult(
    UUID submissionId, String priorAuthorityType, List<PriorAuthorityHistoryEventResult> events) {

  public PriorAuthorityHistoryGroupResult {
    events = List.copyOf(events);
  }
}
