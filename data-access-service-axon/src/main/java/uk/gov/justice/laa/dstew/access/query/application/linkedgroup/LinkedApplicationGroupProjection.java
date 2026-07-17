package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;

/**
 * Independently replayable projection of the current state of each linked application group.
 *
 * <p>Owns only {@code linked_application_group_current_state}. Group membership is queried directly
 * from this table; {@code isLead} is derived at read time from {@code leadApplicationId}, not
 * denormalised onto {@code application_current_state}.
 */
@Component
@ProcessingGroup("linked-application-group-projection")
public class LinkedApplicationGroupProjection {

  private final LinkedApplicationGroupReadRepository groupReadRepository;

  public LinkedApplicationGroupProjection(
      LinkedApplicationGroupReadRepository groupReadRepository) {
    this.groupReadRepository = groupReadRepository;
  }

  /**
   * Creates a {@link LinkedApplicationGroupReadModel} row recording the group's identity, lead, and
   * membership. {@code isLead} is not denormalised here — it is derived at read time from {@code
   * leadApplicationId} on each member's application read model.
   */
  @EventHandler
  public void on(LinkedApplicationGroupCreatedEvent event) {
    groupReadRepository.save(
        LinkedApplicationGroupReadModel.builder()
            .groupId(event.groupId())
            .leadApplicationId(event.leadApplicationId())
            .memberIds(event.memberApplicationIds())
            .createdAt(event.occurredAt())
            .modifiedAt(event.occurredAt())
            .build());
  }

  /** Clears the disposable group current-state table before replay. */
  @ResetHandler
  public void reset() {
    groupReadRepository.deleteAllInBatch();
  }
}
