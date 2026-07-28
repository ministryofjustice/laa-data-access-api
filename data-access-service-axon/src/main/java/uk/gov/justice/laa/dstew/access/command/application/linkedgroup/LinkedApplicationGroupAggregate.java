package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.UUID;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

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
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupAggregate {

  @AggregateIdentifier private UUID groupId;
  private final LinkedApplicationGroupState state = new LinkedApplicationGroupState();

  /** Initialises the group, or adds new members idempotently if it already exists. */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(InitialiseLinkedApplicationGroupCommand command) {
    LinkedApplicationGroupDecider.decideInitialise(state, command).forEach(e -> apply(e));
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

  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
