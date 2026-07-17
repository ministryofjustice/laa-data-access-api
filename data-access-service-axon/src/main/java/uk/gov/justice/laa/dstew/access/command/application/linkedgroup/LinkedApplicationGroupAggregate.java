package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
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
 * <p>The aggregate ID equals the lead application's ID, which allows {@link
 * CreateLinkedApplicationGroupCommand} — targeting the lead {@code ApplicationAggregate} — to
 * implicitly prove the lead exists before this aggregate is created. Because both aggregates share
 * the same identifier, Axon disambiguates their event streams via the {@code type} column in {@code
 * domain_event_entry} (which stores the fully-qualified class name).
 */
@Aggregate
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LinkedApplicationGroupAggregate {

  @AggregateIdentifier private UUID groupId;
  private UUID leadApplicationId;
  private List<UUID> memberApplicationIds;

  /**
   * Initialises the group, or returns idempotently if it was already created.
   *
   * <p>Invariant: the lead application ID must be present in the member list.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(InitialiseLinkedApplicationGroupCommand command) {
    if (groupId != null) {
      return; // already initialised — idempotent
    }
    if (!command.memberApplicationIds().contains(command.leadApplicationId())) {
      throw new IllegalArgumentException(
          "Lead application "
              + command.leadApplicationId()
              + " must be present in the member list");
    }
    apply(
        new LinkedApplicationGroupCreatedEvent(
            command.groupId(),
            command.leadApplicationId(),
            List.copyOf(command.memberApplicationIds()),
            command.occurredAt()));
  }

  @EventSourcingHandler
  void on(LinkedApplicationGroupCreatedEvent event) {
    groupId = event.groupId();
    leadApplicationId = event.leadApplicationId();
    memberApplicationIds = List.copyOf(event.memberApplicationIds());
  }

  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
