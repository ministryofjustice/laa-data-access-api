package uk.gov.justice.laa.dstew.access.command.workitem;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Unassigns the current caseworker from one WorkItem aggregate. */
@Command(routingKey = "workItemId")
public record UnassignWorkItemCommand(
    @TargetEntityId UUID workItemId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
