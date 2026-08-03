package uk.gov.justice.laa.dstew.access.command.application.update;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command to append a new immutable version of an existing Application. */
@Command(routingKey = "applicationId")
public record UpdateApplicationCommand(
    @TargetEntityId UUID applicationId,
    String status,
    Map<String, Object> applicationContent,
    String serialisedRequest,
    Instant occurredAt) {}
