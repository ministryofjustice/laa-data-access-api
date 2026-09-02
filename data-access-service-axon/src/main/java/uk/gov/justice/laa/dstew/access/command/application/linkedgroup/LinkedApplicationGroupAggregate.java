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
 * <p>The aggregate identifier is a deterministic UUID derived from the lead application ID via
 * {@link java.util.UUID#nameUUIDFromBytes}. This ensures all applications that reference the same
 * lead converge on the same group, while remaining distinct from the lead's own UUID (which avoids
 * Axon replaying the lead's event stream against this aggregate — {@code readEvents} queries by
 * identifier only, regardless of aggregate type).
 */
@EventSourced(tagKey = "LinkedApplicationGroupAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupAggregate {

  private UUID groupId;
  private final LinkedApplicationGroupState state = new LinkedApplicationGroupState();

  /** Initialises the group, or adds new members idempotently if it already exists. */
  @CommandHandler
  void handle(InitialiseLinkedApplicationGroupCommand command, EventAppender eventAppender) {
    LinkedApplicationGroupDecider.decideInitialise(state, command)
        .forEach(e -> eventAppender.append(e));
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
