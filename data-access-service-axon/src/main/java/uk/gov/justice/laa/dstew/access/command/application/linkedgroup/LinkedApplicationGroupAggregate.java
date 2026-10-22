package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;

/**
 * Event-sourced consistency boundary that owns group identity, lead assignment, and membership for
 * a set of linked Applications.
 *
 * <p>The aggregate identifier is always the routed {@code groupId}; it does not depend on the lead
 * or member application identifiers carried by the command payload.
 *
 * <p>Command handlers live in {@code command.application.linkedgroup.handler}; this class only
 * folds events.
 */
@EventSourced(tagKey = "LinkedApplicationGroupAggregate", idType = UUID.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupAggregate {

  private UUID groupId;
  private final LinkedApplicationGroupState state = new LinkedApplicationGroupState();

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

  /** Returns the folded state for the dedicated command handlers; handlers must not mutate it. */
  public LinkedApplicationGroupState getState() {
    return state;
  }
}
