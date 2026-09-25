package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UploadedDocumentData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadedDocument;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadedDocumentStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;

/** Independently replayable projection of the current state of each prior-authority submission. */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

  private final PriorAuthorityReadRepository repository;
  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final PriorAuthorityDraftStore priorAuthorityDraftStore;
  private final UploadedDocumentStore uploadedDocumentStore;

  /**
   * Creates the prior-authority current-state projection.
   *
   * @param repository persistence for the projected current state
   * @param priorAuthorityDataStore storage for submitted prior-authority content
   * @param priorAuthorityDraftStore storage for in-progress draft content
   * @param uploadedDocumentStore storage for uploaded document metadata
   */
  public PriorAuthorityProjection(
      PriorAuthorityReadRepository repository,
      PriorAuthorityDataStore priorAuthorityDataStore,
      PriorAuthorityDraftStore priorAuthorityDraftStore,
      UploadedDocumentStore uploadedDocumentStore) {
    this.repository = repository;
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.priorAuthorityDraftStore = priorAuthorityDraftStore;
    this.uploadedDocumentStore = uploadedDocumentStore;
  }

  /** Returns the hydrated current state for the requested prior-authority submission. */
  @QueryHandler
  public PriorAuthorityResult handle(FindPriorAuthorityByPriorAuthorityIdQuery query) {
    UUID priorAuthorityId = query.priorAuthorityId();
    return repository
        .findById(priorAuthorityId)
        .flatMap(result -> hydrate(result, priorAuthorityId))
        .orElse(null);
  }

  /** Confirms whether a current-state projection has reached SUBMITTED. */
  @QueryHandler
  public boolean handle(PriorAuthorityPendingByPriorAuthorityIdQuery query) {
    return repository.findById(query.priorAuthorityId()).map(this::isPending).orElse(false);
  }

  /** Confirms whether a document remains active in the current-state projection. */
  @QueryHandler
  public boolean handle(PriorAuthorityDocumentPresentQuery query) {
    return repository
        .findById(query.priorAuthorityId())
        .map(
            priorAuthority ->
                documentsOf(priorAuthority).stream()
                    .anyMatch(
                        document ->
                            document.documentId().equals(query.documentId())
                                && document.deletedAt() == null))
        .orElse(false);
  }

  private Optional<@NonNull PriorAuthorityResult> hydrate(
      PriorAuthorityReadModel priorAuthority, UUID priorAuthorityId) {
    if (PriorAuthorityStatus.DRAFT.name().equals(priorAuthority.getStatus())) {
      return priorAuthorityDraftStore
          .find(priorAuthorityId)
          .map(PriorAuthorityResult::fromDraft)
          .map(result -> result.withUploadedDocuments(documentsFor(priorAuthority)));
    }
    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(priorAuthorityId, priorAuthority.getDataVersion());
    return Optional.of(
        PriorAuthorityResult.from(priorAuthority, payload, priorAuthority.getStatus())
            .withUploadedDocuments(documentsFor(priorAuthority)));
  }

  private List<PriorAuthorityDocument> documentsFor(PriorAuthorityReadModel priorAuthority) {
    List<UploadedDocumentData> documents = priorAuthority.getUploadedDocumentIds();
    if (documents == null || documents.isEmpty()) {
      return List.of();
    }
    return documents.stream()
        .filter(document -> document.deletedAt() == null)
        .map(
            document ->
                new PriorAuthorityDocument(
                    document.documentId(),
                    document.documentType(),
                    uploadedDocumentStore
                        .findById(document.documentId())
                        .map(UploadedDocument::getOriginalFilename)
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Original filename not found for document %s"
                                        .formatted(document.documentId()))),
                    document.fileType(),
                    document.contentType(),
                    document.size(),
                    document.uploadedAt(),
                    document.sourceService(),
                    null))
        .toList();
  }

  /** Creates the current-state row when a prior-authority draft is started. */
  @EventHandler
  public void on(PriorAuthorityDraftStartedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    createRow(
        event.priorAuthorityId(),
        event.applicationId(),
        0L,
        PriorAuthorityStatus.DRAFT.name(),
        event.occurredAt());
  }

  /** Transitions the existing draft current-state row to submitted. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresentOrElse(
            current -> {
              current.setDataVersion(event.dataVersion());
              current.setStatus(PriorAuthorityStatus.SUBMITTED.name());
              current.setModifiedAt(event.occurredAt());
              repository.save(current);
            },
            () ->
                createRow(
                    event.priorAuthorityId(),
                    event.applicationId(),
                    event.dataVersion(),
                    PriorAuthorityStatus.SUBMITTED.name(),
                    event.occurredAt()));
    queryUpdateEmitter.emit(
        PriorAuthorityPendingByPriorAuthorityIdQuery.class,
        query -> query.priorAuthorityId().equals(event.priorAuthorityId()),
        Boolean.TRUE);
  }

  /** Updates current-state data version after a terminal prior-authority decision. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              current.setDataVersion(event.dataVersion());
              current.setStatus(PriorAuthorityStatus.DECIDED.name());
              current.setModifiedAt(event.occurredAt());
              repository.save(current);
            });
  }

  /** Records the aggregate's uploaded document facts in the replayable current-state projection. */
  @EventHandler
  public void on(PriorAuthorityDocumentUploadedEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            priorAuthority -> {
              List<UploadedDocumentData> documents = new ArrayList<>(documentsOf(priorAuthority));
              documents.add(
                  new UploadedDocumentData(
                      event.documentId(),
                      event.size(),
                      event.fileType(),
                      event.contentType(),
                      event.sourceService(),
                      event.documentType(),
                      event.uploadedAt(),
                      null));
              priorAuthority.setUploadedDocumentIds(List.copyOf(documents));
              repository.save(priorAuthority);
            });
  }

  /** Soft-deletes a document in the replayable current-state projection without removing it. */
  @EventHandler
  public void on(PriorAuthorityDocumentDeletedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            priorAuthority -> {
              List<UploadedDocumentData> documents = new ArrayList<>(documentsOf(priorAuthority));
              documents.replaceAll(
                  document ->
                      document.documentId().equals(event.documentId())
                          ? new UploadedDocumentData(
                              document.documentId(),
                              document.size(),
                              document.fileType(),
                              document.contentType(),
                              document.sourceService(),
                              document.documentType(),
                              document.uploadedAt(),
                              event.deletedAt())
                          : document);
              priorAuthority.setUploadedDocumentIds(List.copyOf(documents));
              repository.save(priorAuthority);
              queryUpdateEmitter.emit(
                  PriorAuthorityDocumentPresentQuery.class,
                  query ->
                      query.priorAuthorityId().equals(event.priorAuthorityId())
                          && query.documentId().equals(event.documentId()),
                  false);
            });
  }

  /** Updates the stored document type for a row already present in the projection. */
  @EventHandler
  public void on(PriorAuthorityDocumentTypeUpdatedEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            priorAuthority -> {
              List<UploadedDocumentData> documents = new ArrayList<>(documentsOf(priorAuthority));
              documents.replaceAll(
                  document ->
                      document.documentId().equals(event.documentId())
                          ? document.withDocumentType(event.documentType())
                          : document);
              priorAuthority.setUploadedDocumentIds(List.copyOf(documents));
              repository.save(priorAuthority);
            });
  }

  private boolean isPending(PriorAuthorityReadModel priorAuthority) {
    return PriorAuthorityStatus.SUBMITTED.name().equals(priorAuthority.getStatus());
  }

  private static List<UploadedDocumentData> documentsOf(PriorAuthorityReadModel priorAuthority) {
    return priorAuthority.getUploadedDocumentIds() == null
        ? List.of()
        : priorAuthority.getUploadedDocumentIds();
  }

  private void createRow(
      UUID priorAuthorityId,
      UUID applicationId,
      long dataVersion,
      String status,
      Instant occurredAt) {
    repository.save(
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .dataVersion(dataVersion)
            .status(status)
            .createdAt(occurredAt)
            .modifiedAt(occurredAt)
            .uploadedDocumentIds(List.of())
            .build());
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
