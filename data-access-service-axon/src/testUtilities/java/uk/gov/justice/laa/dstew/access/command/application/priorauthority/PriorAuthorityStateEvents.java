package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.util.List;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

/** Folds prior-authority event histories into aggregates for tests. */
public final class PriorAuthorityStateEvents {

  private PriorAuthorityStateEvents() {}

  /** Folds a prior-authority event history into an aggregate. */
  public static PriorAuthorityAggregate aggregateOf(List<Object> events) {
    var priorAuthority = new PriorAuthorityAggregate();
    for (var event : events) {
      switch (event) {
        case PriorAuthorityDraftStartedEvent started -> priorAuthority.on(started);
        case PriorAuthorityDraftUpdatedEvent updated -> priorAuthority.on(updated);
        case PriorAuthoritySubmittedEvent submitted -> priorAuthority.on(submitted);
        case PriorAuthorityDecisionMadeEvent decision -> priorAuthority.on(decision);
        case PriorAuthorityDocumentUploadedEvent uploaded -> priorAuthority.on(uploaded);
        case PriorAuthorityDocumentDeletedEvent deleted -> priorAuthority.on(deleted);
        case PriorAuthorityDocumentTypeUpdatedEvent typed -> priorAuthority.on(typed);
        case WorkItemAssigned assigned -> priorAuthority.on(assigned);
        case WorkItemUnassigned unassigned -> priorAuthority.on(unassigned);
        default ->
            throw new IllegalArgumentException("Unsupported prior authority event: " + event);
      }
    }
    return priorAuthority;
  }
}
