package uk.gov.justice.laa.dstew.access.query.application;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;

/** Independently replayable projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository applicationReadRepository;
  private final QueryUpdateEmitter queryUpdateEmitter;

  public ApplicationProjection(
      ApplicationReadRepository applicationReadRepository, QueryUpdateEmitter queryUpdateEmitter) {
    this.applicationReadRepository = applicationReadRepository;
    this.queryUpdateEmitter = queryUpdateEmitter;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public java.util.Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
    return applicationReadRepository.findById(query.applicationId());
  }

  /** Creates the current-state row from an Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    ApplicationReadModel saved =
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
    queryUpdateEmitter.emit(
        FindApplicationByIdQuery.class,
        query -> query.applicationId().equals(event.applicationId()),
        saved);
  }

  /** Updates the linked-group membership after the Application is created. */
  @EventHandler
  public void on(ApplicationLinkedEvent event) {
    applicationReadRepository
        .findById(event.applicationId())
        .ifPresent(
            application -> {
              application.setLeadApplicationId(event.leadApplicationId());
              application.setModifiedAt(event.occurredAt());
              applicationReadRepository.save(application);
            });
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    applicationReadRepository.deleteAllInBatch();
  }
}
