package uk.gov.justice.laa.dstew.access.query.application;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;

  public ApplicationProjection(ApplicationReadRepository applicationReadRepository) {
    this.applicationReadRepository = applicationReadRepository;
  }

  /** Creates the current-state row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    applicationReadRepository.save(
        ApplicationReadModel.builder()
            .applicationId(event.applicationId())
            .status(event.status())
            .laaReference(event.laaReference())
            .applicationContent(event.applicationContent())
            .individuals(event.individuals())
            .schemaVersion(event.schemaVersion())
            .applicationType(event.applicationType())
            .applyApplicationId(event.applyApplicationId())
            .submittedAt(event.submittedAt())
            .officeCode(event.officeCode())
            .usedDelegatedFunctions(event.usedDelegatedFunctions())
            .categoryOfLaw(event.categoryOfLaw() == null ? null : event.categoryOfLaw().name())
            .matterType(event.matterType() == null ? null : event.matterType().name())
            .proceedings(event.proceedings())
            .createdAt(event.occurredAt())
            .modifiedAt(event.occurredAt())
            .build());
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    applicationReadRepository.deleteAllInBatch();
  }
}
