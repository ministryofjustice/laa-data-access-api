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

  @EventSourcingHandler
  void on(PriorAuthoritySubmittedEvent event) {
    state = SUBMITTED;
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
}
