package uk.gov.justice.laa.dstew.access.command.application;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.EntityId;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;

/**
 * Post-submission prior authority, modelled as an Axon aggregate member on the {@link
 * ApplicationAggregate} stream (one stream, no saga). It holds only its invariant lifecycle state
 * ({@code DRAFT} then {@code SUBMITTED}); the draft body lives outside the aggregate.
 */
class PriorAuthority {

  static final String DRAFT = "DRAFT";
  static final String SUBMITTED = "SUBMITTED";

  @EntityId private UUID priorAuthorityId;
  private String state;
  private UUID assignedCaseworkerId;

  PriorAuthority(UUID priorAuthorityId) {
    this.priorAuthorityId = priorAuthorityId;
    this.state = DRAFT;
  }

  /**
   * Updates a prior authority draft in place; only permitted while it is still in {@code DRAFT}.
   */
  @CommandHandler
  void handle(UpdatePriorAuthorityDraftCommand command, Clock clock) {
    requireDraft("update");
    apply(
        new PriorAuthorityDraftUpdatedEvent(
            command.applyApplicationId(), priorAuthorityId, Instant.now(clock)));
  }

  /** Submits a prior authority draft, transitioning it from {@code DRAFT} to {@code SUBMITTED}. */
  @CommandHandler
  void handle(SubmitPriorAuthorityCommand command, Clock clock) {
    requireDraft("submit");
    apply(
        new PriorAuthoritySubmittedEvent(
            command.applyApplicationId(), priorAuthorityId, Instant.now(clock)));
  }

  /**
   * Assigns a caseworker to this (submitted) prior authority. Idempotent when the same caseworker
   * is assigned again; rejected with a conflict when a different caseworker already holds it.
   */
  @CommandHandler
  void handle(AssignPriorAuthorityCaseworkerCommand command, Clock clock) {
    requireSubmitted("assign a caseworker to");
    if (assignedCaseworkerId != null) {
      if (assignedCaseworkerId.equals(command.caseworkerId())) {
        return;
      }
      throw new ConflictException(
          "Prior authority " + priorAuthorityId + " is already assigned to a different caseworker");
    }
    apply(
        new CaseworkerAssignedEvent(
            command.applyApplicationId(),
            priorAuthorityId,
            command.caseworkerId(),
            Instant.now(clock)));
  }

  /**
   * Clears this prior authority's caseworker assignment. Rejected with a conflict when it is not
   * currently assigned.
   */
  @CommandHandler
  void handle(UnassignPriorAuthorityCaseworkerCommand command, Clock clock) {
    if (assignedCaseworkerId == null) {
      throw new ConflictException(
          "Prior authority " + priorAuthorityId + " is not assigned to a caseworker");
    }
    apply(
        new CaseworkerUnassignedEvent(
            command.applyApplicationId(), priorAuthorityId, Instant.now(clock)));
  }

  @EventSourcingHandler
  void on(PriorAuthoritySubmittedEvent event) {
    state = SUBMITTED;
  }

  @EventSourcingHandler
  void on(CaseworkerAssignedEvent event) {
    assignedCaseworkerId = event.caseworkerId();
  }

  @EventSourcingHandler
  void on(CaseworkerUnassignedEvent event) {
    assignedCaseworkerId = null;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PriorAuthority that)) {
      return false;
    }
    return java.util.Objects.equals(priorAuthorityId, that.priorAuthorityId)
        && java.util.Objects.equals(state, that.state);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(priorAuthorityId, state);
  }

  private void requireDraft(String action) {
    if (!DRAFT.equals(state)) {
      throw new ConflictException(
          "Cannot " + action + " prior authority " + priorAuthorityId + "; it is not in DRAFT");
    }
  }

  private void requireSubmitted(String action) {
    if (!SUBMITTED.equals(state)) {
      throw new ConflictException(
          "Cannot " + action + " prior authority " + priorAuthorityId + "; it is not submitted");
    }
  }
}
