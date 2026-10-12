package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that records a prior-authority document after SDS accepts its content. */
@Command(routingKey = "priorAuthorityId")
public record PriorAuthorityDocumentUploadCommand(
    @TargetEntityId UUID priorAuthorityId,
    UUID documentId,
    String sourceService,
    String checksum,
    Instant occurredAt,
    String originalFilename,
    Long fileSize,
    String contentType) {}
