package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.Command;
import org.axonframework.modelling.annotation.TargetEntityId;

/** Command that records an application document after SDS accepts its content. */
@Command(routingKey = "applicationId")
public record ApplicationDocumentUploadCommand(
    @TargetEntityId UUID applicationId,
    UUID documentId,
    String documentType,
    Instant uploadedAt,
    Long size,
    String contentType,
    String checksum,
    String sourceService,
    String originalFilename) {}
