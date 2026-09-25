package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * Event-sourced consistency boundary that owns group identity, the "exactly one lead" invariant,
 * and membership for a set of linked Applications.
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

  /** Adds one application to an already-established group idempotently. */
  @CommandHandler
  void handle(AddApplicationToLinkedGroupCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideAddApplication(state, command)
        .ifPresent(eventAppender::append);
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

  @EntityCreator
  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
