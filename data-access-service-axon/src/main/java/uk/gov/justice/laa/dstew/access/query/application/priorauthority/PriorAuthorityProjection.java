package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/**
 * Independently replayable projection of the current state of each prior-authority submission, used
 * by the application list and work list. Single prior-authority reads use {@link
 * PriorAuthorityQueryHandler}.
 */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

  private final PriorAuthorityReadRepository repository;

  public PriorAuthorityProjection(PriorAuthorityReadRepository repository) {
    this.repository = repository;
  }

  /** Creates the current-state row when a prior-authority draft is started. */
  @EventHandler
  public void on(PriorAuthorityDraftStartedEvent event) {
    repository.save(
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(event.priorAuthorityId())
            .priorAuthorityType(event.priorAuthorityType())
            .applicationId(event.applicationId())
            .officeCode(event.officeCode())
            .status(PriorAuthorityStatus.DRAFT.name())
            .createdAt(event.occurredAt())
            .modifiedAt(event.occurredAt())
            .build());
  }

  /**
   * Updates the current-state row once a prior-authority draft has been submitted, preserving the
   * original creation time when a draft row already exists.
   */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              current.setPriorAuthorityType(event.priorAuthorityType());
              current.setStatus(PriorAuthorityStatus.SUBMITTED.name());
              current.setModifiedAt(event.occurredAt());
              repository.save(current);
            });
  }

  /** Records a terminal prior-authority decision on the current-state row. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              current.setPriorAuthorityType(event.priorAuthorityType());
              current.setDecision(event.overallDecision());
              current.setStatus(PriorAuthorityStatus.DECIDED.name());
              current.setModifiedAt(event.occurredAt());
              repository.save(current);
            });
  }

  /** Appends filename-free document metadata once per document ID. */
  @EventHandler
  public void on(PriorAuthorityDocumentUploadedEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              if (current.getUploadedDocuments().stream()
                  .anyMatch(document -> document.documentId().equals(event.documentId()))) {
                return;
              }
              List<DocumentMetadata> documents = new ArrayList<>(current.getUploadedDocuments());
              documents.add(
                  new DocumentMetadata(
                      event.documentId(),
                      null,
                      event.uploadedAt(),
                      event.size().orElse(null),
                      event.contentType().orElse(null),
                      event.checksum(),
                      event.sourceService().orElse(null),
                      false,
                      event.fileSuffix().orElse(null)));
              current.setUploadedDocuments(List.copyOf(documents));
              current.setModifiedAt(event.uploadedAt());
              repository.save(current);
            });
  }

  /** Marks projected document metadata deleted. */
  @EventHandler
  public void on(PriorAuthorityDocumentDeletedEvent event) {
    updateDocument(
        event.priorAuthorityId(),
        event.documentId(),
        event.deletedAt(),
        document -> document.withDeleted(true));
  }

  /** Records the latest document type in projected document metadata. */
  @EventHandler
  public void on(PriorAuthorityDocumentTypeUpdatedEvent event) {
    updateDocument(
        event.priorAuthorityId(),
        event.documentId(),
        event.occurredAt(),
        document -> document.withDocumentType(event.documentType().orElse(null)));
  }

  private void updateDocument(
      UUID priorAuthorityId,
      UUID documentId,
      Instant occurredAt,
      UnaryOperator<DocumentMetadata> update) {
    repository
        .findById(priorAuthorityId)
        .ifPresent(
            current -> {
              current.setUploadedDocuments(
                  current.getUploadedDocuments().stream()
                      .map(
                          document ->
                              document.documentId().equals(documentId)
                                  ? update.apply(document)
                                  : document)
                      .toList());
              current.setModifiedAt(occurredAt);
              repository.save(current);
            });
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
