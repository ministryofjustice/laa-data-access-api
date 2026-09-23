package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that sets or replaces the type of an uploaded Prior Authority document. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentTypeUpdateCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String documentType,
    String serialisedRequest,
    Instant occurredAt) {}
