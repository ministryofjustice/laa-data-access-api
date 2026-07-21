package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Assigns a caseworker to one Application aggregate. */
@Command(routingKey = "applicationId")
public record AssignCaseworkerToApplicationCommand(
    @TargetEntityId UUID applicationId,
    UUID caseworkerId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
