package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Thin replayable event recording a direct work-item assignment. */
@Event(namespace = "access", name = "WorkItemAssigned")
public record WorkItemAssigned(
    @RetainedField @EventTag UUID workItemId,
    @RetainedField WorkItemType workItemType,
    @RetainedField UUID applicationId,
    @RetainedField long itemVersion,
    @RetainedField long assignmentVersion,
    @RetainedField UUID caseworkerId,
    @RetainedField Instant occurredAt)
    implements WorkItemAssignmentChange, GenesisScoped {

  /** Events stored before {@code applicationId} was recorded name no genesis. */
  @Override
  public Set<UUID> genesisIds() {
    return applicationId == null ? Set.of() : GenesisScoped.ids(applicationId);
  }
}
