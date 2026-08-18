package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Unassigns the current caseworker from one Application aggregate. */
@Command(routingKey = "applicationId")
public record UnassignCaseworkerFromApplicationCommand(
    @TargetEntityId UUID applicationId,
    String serialisedRequest,
    String eventDescription,
    Instant occurredAt) {}
