package uk.gov.justice.laa.dstew.access.command.worklist.assign;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Assigns every active member of a linked group, addressed through one of its work items. */
@Command(routingKey = "groupId")
public record AssignLinkedGroupWorkItemCommand(
    @TargetEntityId UUID groupId,
    UUID workItemId,
    UUID caseworkerId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
