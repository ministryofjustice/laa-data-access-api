package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;

/**
 * Event-sourced consistency boundary that owns group identity, lead assignment, and membership for
 * a set of linked Applications.
 *
 * <p>The aggregate identifier is always the routed {@code groupId}; it does not depend on the lead
 * or member application identifiers carried by the command payload.
 */
@EventSourced(tagKey = "LinkedApplicationGroupAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupAggregate {

  private UUID groupId;
  private final LinkedApplicationGroupState state = new LinkedApplicationGroupState();

  /** Establishes the group idempotently when the routed group stream is empty. */
  @CommandHandler
  void handle(EstablishLinkedApplicationGroupCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideEstablish(state, command).ifPresent(eventAppender::append);
  }

  /**
   * Creational counterpart of {@link #handle(EstablishLinkedApplicationGroupCommand,
   * EventAppender)}, invoked when the routed group id has no prior events. Axon 5.4 only routes a
   * command to the no-arg-constructed entity when a {@code static} handler is registered for it;
   * without this overload the command would fail with {@code
   * EntityMissingForInstanceCommandHandlerException} instead of being established.
   */
  @CommandHandler
  static void handle(
      EstablishLinkedApplicationGroupCommand command,
      EventAppender eventAppender,
      ProcessingContext context) {
    LinkedApplicationGroupDecider.decideEstablish(new LinkedApplicationGroupState(), command)
        .ifPresent(eventAppender::append);
  }

  /** Adds one application to an already-established group idempotently. */
  @CommandHandler
  void handle(AddApplicationToLinkedGroupCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideAddApplication(state, command)
        .ifPresent(eventAppender::append);
  }

  /**
   * Creational counterpart required so a missing group raises the decider's {@link
   * IllegalStateException} instead of Axon's {@code
   * EntityMissingForInstanceCommandHandlerException}.
   *
   * <p>On a blank state this always throws, so the call site's basic block never completes and
   * JaCoCo cannot mark the line as covered even though the overload is exercised by tests.
   */
  @CommandHandler
  @ExcludeFromGeneratedCodeCoverage
  static void handle(AddApplicationToLinkedGroupCommand command, ProcessingContext context) {
    LinkedApplicationGroupDecider.decideAddApplication(new LinkedApplicationGroupState(), command);
  }

  /** Changes the lead of an established group. */
  @CommandHandler
  void handle(ChangeLinkedGroupLeadCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideChangeLead(state, command).ifPresent(eventAppender::append);
  }

  /**
   * Creational counterpart required so a missing group raises the decider's {@link
   * IllegalStateException} instead of Axon's {@code
   * EntityMissingForInstanceCommandHandlerException}.
   *
   * <p>On a blank state this always throws, so the call site's basic block never completes and
   * JaCoCo cannot mark the line as covered even though the overload is exercised by tests.
   */
  @CommandHandler
  @ExcludeFromGeneratedCodeCoverage
  static void handle(ChangeLinkedGroupLeadCommand command, ProcessingContext context) {
    LinkedApplicationGroupDecider.decideChangeLead(new LinkedApplicationGroupState(), command);
  }

  /** Removes a non-lead member from an established group. */
  @CommandHandler
  void handle(RemoveApplicationFromLinkedGroupCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideRemoveApplication(state, command)
        .ifPresent(eventAppender::append);
  }

  /**
   * Creational counterpart required so a missing group raises the decider's {@link
   * IllegalStateException} instead of Axon's {@code
   * EntityMissingForInstanceCommandHandlerException}.
   *
   * <p>On a blank state this always throws, so the call site's basic block never completes and
   * JaCoCo cannot mark the line as covered even though the overload is exercised by tests.
   */
  @CommandHandler
  @ExcludeFromGeneratedCodeCoverage
  static void handle(RemoveApplicationFromLinkedGroupCommand command, ProcessingContext context) {
    LinkedApplicationGroupDecider.decideRemoveApplication(
        new LinkedApplicationGroupState(), command);
  }

  /**
   * Validates that the requested application remains the current lead without appending an event.
   */
  @CommandHandler
  void handle(ValidatePriorAuthorityGroupEligibilityCommand command) {
    LinkedApplicationGroupDecider.validatePriorAuthorityGroupEligibility(state, command);
  }

  /**
   * Creational counterpart required so a missing group raises the decider's {@link
   * uk.gov.justice.laa.dstew.access.exception.LinkedApplicationGroupVersionConflictException}
   * instead of Axon's {@code EntityMissingForInstanceCommandHandlerException}.
   *
   * <p>On a blank state this always throws, so the call site's basic block never completes and
   * JaCoCo cannot mark the line as covered even though the overload is exercised by tests.
   */
  @CommandHandler
  @ExcludeFromGeneratedCodeCoverage
  static void handle(
      ValidatePriorAuthorityGroupEligibilityCommand command, ProcessingContext context) {
    LinkedApplicationGroupDecider.validatePriorAuthorityGroupEligibility(
        new LinkedApplicationGroupState(), command);
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupCreatedEvent event) {
    LinkedApplicationGroupEvolve.apply(state, event);
    this.groupId = state.groupId;
  }

  @EventSourcingHandler
  void on(MemberAddedToGroupEvent event) {
    LinkedApplicationGroupEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupLeadChangedEvent event) {
    LinkedApplicationGroupEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(MemberRemovedFromGroupEvent event) {
    LinkedApplicationGroupEvolve.apply(state, event);
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupDissolvedEvent event) {
    LinkedApplicationGroupEvolve.apply(state, event);
  }

  @EntityCreator
  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
