package uk.gov.justice.laa.dstew.access.query.application.linkedgroup;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupCreatedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

/**
 * Independently replayable projection of the current state of each linked application group.
 *
 * <p>When a group is created, this projection: (1) saves the group read model, (2) marks the lead
 * application with {@code isLead = true}, and (3) sets {@code groupId} on every member.
 */
@Component
@ProcessingGroup("linked-application-group-projection")
public class LinkedApplicationGroupProjection {

  private final LinkedApplicationGroupReadRepository groupReadRepository;
  private final ApplicationReadRepository applicationReadRepository;

  public LinkedApplicationGroupProjection(
      LinkedApplicationGroupReadRepository groupReadRepository,
      ApplicationReadRepository applicationReadRepository) {
    this.groupReadRepository = groupReadRepository;
    this.applicationReadRepository = applicationReadRepository;
  }

  /** Creates the group read model and updates every member's application read model. */
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

    event
        .memberApplicationIds()
        .forEach(
            memberId ->
                applicationReadRepository
                    .findById(memberId)
                    .ifPresent(
                        app -> {
                          app.setGroupId(event.groupId());
                          app.setIsLead(memberId.equals(event.leadApplicationId()));
                          applicationReadRepository.save(app);
                        }));
  }

  /** Clears the disposable group current-state table before replay. */
  @ResetHandler
  public void reset() {
    groupReadRepository.deleteAllInBatch();
  }
}
