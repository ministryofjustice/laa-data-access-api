package uk.gov.justice.laa.dstew.access.command.worklist;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Internal aggregate-targeted form of a generic direct unassignment command. */
@Command(routingKey = "aggregateId")
public record DirectWorkItemUnassignmentCommand(
    @TargetEntityId UUID aggregateId,
    WorkItemId workItemId,
    long expectedAssignmentVersion,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}

