package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that performs and finalises a prior-authority document upload. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentUploadCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String documentType,
    String sourceService,
    String checksum,
    String serialisedRequest,
    Instant occurredAt,
    String originalFilename,
    Long fileSize) {}
