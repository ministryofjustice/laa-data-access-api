package uk.gov.justice.laa.dstew.access.command.workitem;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Assigns a caseworker to one WorkItem aggregate. */
@Command(routingKey = "workItemId")
public record AssignWorkItemCommand(
    @TargetEntityId UUID workItemId,
    UUID caseworkerId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt,
    boolean fanOut) {}
