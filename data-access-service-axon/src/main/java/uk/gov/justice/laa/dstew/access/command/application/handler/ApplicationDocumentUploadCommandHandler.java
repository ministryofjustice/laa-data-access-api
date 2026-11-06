package uk.gov.justice.laa.dstew.access.command.application.handler;

import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.document.DocumentFileSuffix;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles application draft document uploads. */
@Component
public class ApplicationDocumentUploadCommandHandler {

  /** Records metadata for a document already accepted by SDS. */
  @CommandHandler
  public UUID handle(
      ApplicationDocumentUploadCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationDraft(application);
    if (command.originalFilename() == null || command.originalFilename().isBlank()) {
      throw new ValidationException(List.of("Original filename is required"));
    }
    ApplicationCommandHandlerSupport.requireDraft(application, command.applicationId());
    boolean existing =
        application.getState().getUploadedDocuments().stream()
            .map(DocumentMetadata::documentId)
            .anyMatch(id -> id.equals(command.documentId()));
    if (existing) {
      throw new ValidationException(List.of("Document ID already records a different upload"));
    }

    eventAppender.append(
        new ApplicationDocumentUploadedEvent(
            command.applicationId(),
            command.documentId(),
            command.documentType(),
            command.uploadedAt(),
            command.size(),
            command.contentType(),
            command.checksum(),
            command.sourceService(),
            null,
            DocumentFileSuffix.fromFilename(command.originalFilename()),
            command.originalFilename()));
    return command.documentId();
  }
}
