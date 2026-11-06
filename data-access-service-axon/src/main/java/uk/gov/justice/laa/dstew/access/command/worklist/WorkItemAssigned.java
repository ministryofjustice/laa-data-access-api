package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;

/** Thin replayable event recording a direct work-item assignment. */
@Event(namespace = "access", name = "WorkItemAssigned")
public record WorkItemAssigned(
    @EventTag UUID workItemId,
    WorkItemType workItemType,
    UUID applicationId,
    long itemVersion,
    long assignmentVersion,
    UUID caseworkerId,
    Instant occurredAt)
    implements WorkItemAssignmentChange, GenesisScoped {

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
