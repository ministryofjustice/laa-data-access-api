package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;

/** Independently replayable projection of the current state of each prior-authority submission. */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

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
  @Nullable
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
  public boolean handle(PriorAuthoritySubmittedByPriorAuthorityIdQuery query) {
    return repository.findById(query.priorAuthorityId()).map(this::isPending).orElse(false);
  }

  private Optional<@NonNull PriorAuthorityResult> hydrate(
      PriorAuthorityReadModel priorAuthority, UUID priorAuthorityId) {
    if (PriorAuthorityStatus.DRAFT.name().equals(priorAuthority.getStatus())) {
      return priorAuthorityDraftStore.find(priorAuthorityId).map(PriorAuthorityResult::fromDraft);
    }
    PriorAuthorityDataPayload payload =
        priorAuthorityDataStore.get(priorAuthorityId, priorAuthority.getDataVersion());
    return Optional.of(
        PriorAuthorityResult.from(priorAuthority, payload, priorAuthority.getStatus()));
  }

  /** Creates the current-state row when a prior-authority draft is started. */
  @EventHandler
  public void on(PriorAuthorityDraftStartedEvent event) {
    repository.save(
        PriorAuthorityReadModel.builder()
            .priorAuthorityId(event.priorAuthorityId())
            .priorAuthorityType(event.priorAuthorityType())
            .applicationId(event.applicationId())
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

  private boolean isPending(PriorAuthorityReadModel priorAuthority) {
    return PriorAuthorityStatus.SUBMITTED.name().equals(priorAuthority.getStatus());
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
