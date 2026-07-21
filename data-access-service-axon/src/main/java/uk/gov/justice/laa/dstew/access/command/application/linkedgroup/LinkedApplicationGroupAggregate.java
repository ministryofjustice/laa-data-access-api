package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
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
  void handle(InitialiseLinkedApplicationGroupCommand command, EventAppender eventAppender) {
    if (groupId != null) {
      // Group already exists — add any members not yet in it.
      command.memberApplicationIds().stream()
          .filter(id -> !memberApplicationIds.contains(id))
          .forEach(
              id ->
                  eventAppender.append(
                      new MemberAddedToGroupEvent(groupId, id, command.occurredAt())));
      return;
    }
    if (!command.memberApplicationIds().contains(command.leadApplicationId())) {
      throw new IllegalArgumentException(
          "Lead application "
              + command.leadApplicationId()
              + " must be present in the member list");
    }
    eventAppender.append(
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

  @EntityCreator
  protected LinkedApplicationGroupAggregate() {
    // Required by Axon when rebuilding the aggregate from its event stream.
  }
}
