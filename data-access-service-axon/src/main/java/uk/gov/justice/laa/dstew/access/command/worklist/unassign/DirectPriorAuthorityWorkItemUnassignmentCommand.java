package uk.gov.justice.laa.dstew.access.command.worklist.unassign;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Internal PA aggregate-targeted form of a generic direct unassignment command. */
@Command(routingKey = "workItemId")
public record DirectPriorAuthorityWorkItemUnassignmentCommand(
    @TargetEntityId UUID workItemId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
