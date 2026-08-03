package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Records that automatic assessment completed without an automatic grant. */
@Command(routingKey = "applicationId")
public record MarkApplicationReadyCommand(
    @TargetEntityId UUID applicationId,
    long expectedApplicationVersion,
    String serialisedRequest,
    Instant occurredAt) {}
