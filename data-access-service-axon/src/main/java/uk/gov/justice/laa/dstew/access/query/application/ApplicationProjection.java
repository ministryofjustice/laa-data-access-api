package uk.gov.justice.laa.dstew.access.query.application;

import java.util.Optional;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;

/** Subscribing projection of the current state of each Application. */
@Component
@ProcessingGroup("application-projection")
public class ApplicationProjection {

  private final ApplicationReadRepository repository;

  public ApplicationProjection(ApplicationReadRepository repository) {
    this.repository = repository;
  }

  /** Returns the current-state projection for the requested Application. */
  @QueryHandler
  public Optional<ApplicationReadModel> handle(FindApplicationByIdQuery query) {
    return repository.findById(query.applyApplicationId());
  }

  /** Creates the current-state row from a Application's creation event. */
  @EventHandler
  public void on(ApplicationCreatedEvent event) {
    repository.save(
        ApplicationReadModel.builder()
            .applyApplicationId(event.applyApplicationId())
            .status(event.status())
            .laaReference(event.laaReference())
            .schemaVersion(event.schemaVersion())
            .applicationType(event.applicationType())
            .submittedAt(event.submittedAt())
            .officeCode(event.officeCode())
            .usedDelegatedFunctions(event.usedDelegatedFunctions())
            .categoryOfLaw(event.categoryOfLaw() == null ? null : event.categoryOfLaw().name())
            .matterType(event.matterType() == null ? null : event.matterType().name())
            .createdAt(event.occurredAt())
            .build());
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
