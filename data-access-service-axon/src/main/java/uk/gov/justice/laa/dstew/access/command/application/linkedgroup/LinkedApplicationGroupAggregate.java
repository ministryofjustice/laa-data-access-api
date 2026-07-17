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
  private UUID leadApplicationId;
  private List<UUID> memberApplicationIds;

  /**
   * Initialises the group, or adds new members idempotently if it already exists.
   *
   * <p>On first call: validates the lead is in the member list, then emits {@link
   * LinkedApplicationGroupCreatedEvent}.
   *
   * <p>On subsequent calls: diffs {@code command.memberApplicationIds()} against the current member
   * list and emits {@link MemberAddedToGroupEvent} for each member not already present. This
   * handles the case where a second (or later) application joins an existing group.
   */
  @CommandHandler
  @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
  void handle(InitialiseLinkedApplicationGroupCommand command) {
    if (groupId != null) {
      // Group already exists — add any members not yet in it.
      command.memberApplicationIds().stream()
          .filter(id -> !memberApplicationIds.contains(id))
          .forEach(id -> apply(new MemberAddedToGroupEvent(groupId, id, command.occurredAt())));
      return;
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
    memberApplicationIds = new java.util.ArrayList<>(event.memberApplicationIds());
  }

  @EventSourcingHandler
  void on(MemberAddedToGroupEvent event) {
    memberApplicationIds.add(event.memberId());
  }

  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
