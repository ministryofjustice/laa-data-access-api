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
import uk.gov.justice.laa.dstew.access.command.application.UploadDocument;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Handles application draft document uploads. */
@Component
public class ApplicationDocumentUploadCommandHandler {

  /** Records metadata for a document already accepted by SDS. */
  @CommandHandler
  public UUID handle(
      ApplicationDocumentUploadCommand command,
      @InjectEntity(idProperty = "applicationId") ApplicationAggregate application,
      ApplicationDraftStore draftStore,
      EventAppender eventAppender) {
    ApplicationCommandHandlerSupport.requireApplicationDraft(application, command.applicationId());
    if (command.originalFilename() == null || command.originalFilename().isBlank()) {
      throw new ValidationException(List.of("Original filename is required"));
    }
    var current =
        ApplicationCommandHandlerSupport.requireDraft(command.applicationId(), draftStore);
    boolean existing =
        application.getState().getUploadedDocuments().stream()
            .map(UploadDocument::documentId)
            .anyMatch(id -> id.equals(command.documentId()));
    if (existing) {
      throw new ValidationException(List.of("Document ID already records a different upload"));
    }

    draftStore.upsert(
        command.applicationId(),
        current.withDocumentFilename(command.documentId(), command.originalFilename()),
        current.serialisedRequest(),
        command.uploadedAt());
    eventAppender.append(
        new ApplicationDocumentUploadedEvent(
            command.applicationId(),
            command.documentId(),
            command.documentType(),
            command.uploadedAt(),
            command.size(),
            command.contentType(),
            command.checksum(),
            command.sourceService()));
    return command.documentId();
  }
}
