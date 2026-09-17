package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that removes a document from a prior-authority draft. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentDeleteCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String serialisedRequest,
    Instant occurredAt) {}
