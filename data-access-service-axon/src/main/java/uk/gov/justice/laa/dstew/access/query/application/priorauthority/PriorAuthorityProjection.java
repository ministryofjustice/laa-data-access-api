package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;

/** Independently replayable projection of the current state of each prior-authority submission. */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

  private static final String IN_PROGRESS = "IN_PROGRESS";

  private final PriorAuthorityReadRepository repository;
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
      PriorAuthorityDataStore priorAuthorityDataStore,
      PriorAuthorityDraftStore priorAuthorityDraftStore) {
    this.repository = repository;
    this.priorAuthorityDataStore = priorAuthorityDataStore;
    this.priorAuthorityDraftStore = priorAuthorityDraftStore;
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

  /** Confirms whether a current-state projection exists for the requested submission. */
  @QueryHandler
  public boolean handle(PriorAuthorityExistsBySubmissionIdQuery query) {
    return repository.existsById(query.submissionId());
  }

  /** Confirms whether a current-state projection has reached PENDING. */
  @QueryHandler
  public boolean handle(PriorAuthorityPendingByPriorAuthorityIdQuery query) {
    return repository
        .findById(query.priorAuthorityId())
        .map(priorAuthority -> "PENDING".equals(priorAuthority.getStatus()))
        .orElse(false);
  }

  private Optional<@NonNull PriorAuthorityResult> hydrate(
      PriorAuthorityReadModel priorAuthority, UUID priorAuthorityId) {
    if (IN_PROGRESS.equals(priorAuthority.getStatus())) {
      return priorAuthorityDraftStore.find(priorAuthorityId).map(PriorAuthorityResult::fromDraft);
    }
    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(priorAuthorityId, priorAuthority.getDataVersion());
    return Optional.of(PriorAuthorityResult.from(priorAuthority, payload.content()));
  }

  /** Creates the current-state row from a prior-authority creation event. */
  @EventHandler
  public void on(PriorAuthorityCreatedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    createRow(
        event.priorAuthorityId(),
        event.applicationId(),
        event.dataVersion(),
        event.status(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  /** Creates the current-state row when a prior-authority draft is started. */
  @EventHandler
  public void on(PriorAuthorityDraftStartedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    createRow(
        event.priorAuthorityId(),
        event.applicationId(),
        0L,
        IN_PROGRESS,
        event.occurredAt(),
        queryUpdateEmitter);
  }

  /** Creates the current-state row once a prior-authority draft has been submitted. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    createRow(
        event.priorAuthorityId(),
        event.applicationId(),
        event.dataVersion(),
        event.status(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  private void createRow(
      UUID priorAuthorityId,
      UUID applicationId,
      long dataVersion,
      String status,
      Instant createdAt,
      QueryUpdateEmitter queryUpdateEmitter) {
    repository.save(
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .dataVersion(dataVersion)
            .status(status)
            .createdAt(createdAt)
            .build());
    queryUpdateEmitter.emit(
        PriorAuthorityExistsBySubmissionIdQuery.class,
        query -> query.submissionId().equals(priorAuthorityId),
        Boolean.TRUE);
    if ("PENDING".equals(status)) {
      queryUpdateEmitter.emit(
          PriorAuthorityPendingByPriorAuthorityIdQuery.class,
          query -> query.priorAuthorityId().equals(priorAuthorityId),
          Boolean.TRUE);
    }
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
