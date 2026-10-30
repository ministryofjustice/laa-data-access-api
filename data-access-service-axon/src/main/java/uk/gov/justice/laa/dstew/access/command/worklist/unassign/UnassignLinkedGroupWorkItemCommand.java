package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Unassigns every assigned active member of a linked group through one of its work items. */
@Command(routingKey = "groupId")
public record UnassignLinkedGroupWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID workItemId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
