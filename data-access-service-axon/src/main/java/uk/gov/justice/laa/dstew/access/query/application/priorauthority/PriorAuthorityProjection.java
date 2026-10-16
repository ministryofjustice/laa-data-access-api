package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;

/** Independently replayable projection of the current state of each prior-authority submission. */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

  private final PriorAuthorityReadRepository repository;
  private final PriorAuthorityReadQueryGateway readQueryGateway;
  private final PriorAuthorityCurrentStateAccessPolicy accessPolicy;
  private final PriorAuthorityDataStore priorAuthorityDataStore;
  private final PriorAuthorityDraftStore priorAuthorityDraftStore;

  /**
   * Creates the prior-authority current-state projection.
   *
   * @param repository persistence for the projected current state
   * @param priorAuthorityDataStore storage for submitted prior-authority content
   * @param priorAuthorityDraftStore storage for in-progress draft content
   */
  public PriorAuthorityProjection(
      PriorAuthorityReadRepository repository,
      PriorAuthorityReadQueryGateway readQueryGateway,
      PriorAuthorityCurrentStateAccessPolicy accessPolicy,
      PriorAuthorityDataStore priorAuthorityDataStore,
      PriorAuthorityDraftStore priorAuthorityDraftStore) {
    this.repository = repository;
    this.readQueryGateway = readQueryGateway;
    this.accessPolicy = accessPolicy;
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.priorAuthorityDraftStore = priorAuthorityDraftStore;
  }

  /** Returns the hydrated current state for the requested prior-authority submission. */
  @Nullable
  @QueryHandler
  public PriorAuthorityResult handle(FindPriorAuthorityByPriorAuthorityIdQuery query) {
    UUID priorAuthorityId = query.priorAuthorityId();
    return readQueryGateway
        .findPriorAuthority(
            (root, criteriaQuery, cb) -> cb.equal(root.get("priorAuthorityId"), priorAuthorityId),
            accessPolicy.restrictionFor(query.accessScope()))
        .flatMap(result -> hydrate(result, priorAuthorityId))
        .orElse(null);
  }

  /** Returns an owned, active document, hydrating its filename when available. */
  @Nullable
  @QueryHandler
  public EvidenceDocument handle(FindPriorAuthorityDocumentQuery query) {
    return repository
        .findById(query.priorAuthorityId())
        .flatMap(
            priorAuthority ->
                priorAuthority.getUploadedDocuments().stream()
                    .filter(document -> query.documentId().equals(document.documentId()))
                    .filter(document -> !document.deleted())
                    .findFirst()
                    .map(
                        document ->
                            new EvidenceDocument(
                                document.documentId(),
                                document.documentType(),
                                documentFilename(priorAuthority, document.documentId()),
                                null,
                                document.contentType(),
                                document.size(),
                                document.uploadedAt(),
                                document.sourceService(),
                                document.checksum(),
                                document.fileSuffix())))
        .orElse(null);
  }

  /** Confirms whether a current-state projection has reached SUBMITTED. */
  @QueryHandler
  public boolean handle(PriorAuthoritySubmittedByPriorAuthorityIdQuery query) {
    return repository.findById(query.priorAuthorityId()).map(this::isPending).orElse(false);
  }

  private String documentFilename(PriorAuthorityReadModel priorAuthority, UUID documentId) {
    if (PriorAuthorityStatus.DRAFT.name().equals(priorAuthority.getStatus())) {
      return priorAuthorityDraftStore
          .find(priorAuthority.getPriorAuthorityId())
          .map(draft -> draft.documentFilenames().get(documentId))
          .orElse(null);
    }
    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(
            priorAuthority.getPriorAuthorityId(), priorAuthority.getDataVersion());
    return payload == null ? null : payload.documentFilenames().get(documentId);
  }

  private Optional<@NonNull PriorAuthorityResult> hydrate(
      PriorAuthorityReadModel priorAuthority, UUID priorAuthorityId) {
    if (PriorAuthorityStatus.DRAFT.name().equals(priorAuthority.getStatus())) {
      return priorAuthorityDraftStore
          .find(priorAuthorityId)
          .map(draft -> PriorAuthorityResult.from(priorAuthority, draft));
    }
    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(priorAuthorityId, priorAuthority.getDataVersion());
    return Optional.of(PriorAuthorityResult.from(priorAuthority, payload));
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
            .dataVersion(0L)
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
  public void on(PriorAuthoritySubmittedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              current.setPriorAuthorityType(event.priorAuthorityType());
              current.setDataVersion(event.dataVersion());
              current.setStatus(PriorAuthorityStatus.SUBMITTED.name());
              current.setModifiedAt(event.occurredAt());
              repository.save(current);
            });

    queryUpdateEmitter.emit(
        PriorAuthoritySubmittedByPriorAuthorityIdQuery.class,
        query -> query.priorAuthorityId().equals(event.priorAuthorityId()),
        Boolean.TRUE);
  }

  /** Updates a current-state data version after a terminal prior-authority decision. */
  @EventHandler
  public void on(PriorAuthorityDecisionMadeEvent event) {
    repository
        .findById(event.priorAuthorityId())
        .ifPresent(
            current -> {
              current.setPriorAuthorityType(event.priorAuthorityType());
              current.setDataVersion(event.dataVersion());
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
                      event.size(),
                      event.contentType(),
                      event.checksum(),
                      event.sourceService(),
                      false,
                      event.fileSuffix()));
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
        document -> document.withDocumentType(event.documentType()));
  }

  private boolean isPending(PriorAuthorityReadModel priorAuthority) {
    return PriorAuthorityStatus.SUBMITTED.name().equals(priorAuthority.getStatus());
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
