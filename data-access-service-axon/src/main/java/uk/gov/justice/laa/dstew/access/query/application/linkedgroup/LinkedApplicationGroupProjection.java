package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import java.util.ArrayList;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupDissolvedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupLeadChangedEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberAddedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.MemberRemovedFromGroupEvent;

/**
 * Independently replayable projection of the current state of each linked application group.
 *
 * <p>Owns only {@code linked_application_group_current_state}. Group membership is queried directly
 * from this table; {@code isLead} is derived at read time from the group's {@code
 * leadApplicationId}.
 */
@Component
@Namespace("linked-application-group-projection")
public class LinkedApplicationGroupProjection {

  private final LinkedApplicationGroupReadRepository groupReadRepository;

  public LinkedApplicationGroupProjection(
      LinkedApplicationGroupReadRepository groupReadRepository) {
    this.groupReadRepository = groupReadRepository;
  }

  /**
   * Creates a {@link LinkedApplicationGroupReadModel} row recording the group's identity, lead, and
   * membership. {@code isLead} is derived at read time from the group's {@code leadApplicationId}.
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event) {
    groupReadRepository.save(
        LinkedApplicationGroupReadModel.builder()
            .groupId(event.groupId())
            .leadApplicationId(event.leadApplicationId())
            .memberIds(event.memberApplicationIds())
            .version(0L)
            .createdAt(event.occurredAt())
            .modifiedAt(event.occurredAt())
            .build());
  }

  /**
   * Adds a new member to an existing group's read model. Appends the member ID to {@code memberIds}
   * and updates {@code modifiedAt}.
   */
  @EventHandler
  public void on(MemberAddedToGroupEvent event) {
    groupReadRepository
        .findById(event.groupId())
        .ifPresent(
            group -> {
              if (group.getMemberIds().contains(event.memberId())) {
                return;
              }
              var memberIds = new ArrayList<>(group.getMemberIds());
              memberIds.add(event.memberId());
              group.setMemberIds(memberIds);
              group.setVersion(group.getVersion() + 1);
              group.setModifiedAt(event.occurredAt());
              groupReadRepository.save(group);
            });
  }

  /** Changes the projected lead and applies the version carried by the event. */
  @EventHandler
  public void on(LinkedApplicationGroupLeadChangedEvent event) {
    groupReadRepository
        .findById(event.groupId())
        .ifPresent(
            group -> {
              group.setLeadApplicationId(event.newLeadApplicationId());
              group.setVersion(event.groupVersion());
              group.setModifiedAt(event.occurredAt());
              groupReadRepository.save(group);
            });
  }

  /** Removes a member and applies the version carried by the event. */
  @EventHandler
  public void on(MemberRemovedFromGroupEvent event) {
    groupReadRepository
        .findById(event.groupId())
        .ifPresent(
            group -> {
              var memberIds = new ArrayList<>(group.getMemberIds());
              memberIds.remove(event.memberId());
              group.setMemberIds(memberIds);
              group.setVersion(event.groupVersion());
              group.setModifiedAt(event.occurredAt());
              groupReadRepository.save(group);
            });
  }

  /** Deletes the current-state row when the group is dissolved. */
  @EventHandler
  public void on(LinkedApplicationGroupDissolvedEvent event) {
    groupReadRepository.deleteById(event.groupId());
  }

  /** Clears the disposable group current-state table before replay. */
  @ResetHandler
  public void reset() {
    groupReadRepository.deleteAllInBatch();
  }
}
