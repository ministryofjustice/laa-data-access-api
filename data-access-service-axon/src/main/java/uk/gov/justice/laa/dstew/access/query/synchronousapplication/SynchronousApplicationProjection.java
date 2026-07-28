package uk.gov.justice.laa.dstew.access.query.synchronousapplication;

import java.util.Optional;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationCreatedEvent;

/** Subscribing projection of the current state of each SynchronousApplication. */
@Component
@ProcessingGroup("synchronous-application-projection")
public class SynchronousApplicationProjection {

  private final SynchronousApplicationReadRepository repository;

  public SynchronousApplicationProjection(SynchronousApplicationReadRepository repository) {
    this.repository = repository;
  }

  /** Returns the current-state projection for the requested SynchronousApplication. */
  @QueryHandler
  public Optional<SynchronousApplicationReadModel> handle(
      FindSynchronousApplicationByIdQuery query) {
    return repository.findById(query.applyApplicationId());
  }

  /** Creates the current-state row from a SynchronousApplication's creation event. */
  @EventHandler
  public void on(SynchronousApplicationCreatedEvent event) {
    repository.save(
        SynchronousApplicationReadModel.builder()
            .applyApplicationId(event.applyApplicationId())
            .status(event.status())
            .laaReference(event.laaReference())
            .applicationContent(event.applicationContent())
            .individuals(event.individuals())
            .schemaVersion(event.schemaVersion())
            .applicationType(event.applicationType())
            .submittedAt(event.submittedAt())
            .officeCode(event.officeCode())
            .usedDelegatedFunctions(event.usedDelegatedFunctions())
            .categoryOfLaw(event.categoryOfLaw() == null ? null : event.categoryOfLaw().name())
            .matterType(event.matterType() == null ? null : event.matterType().name())
            .proceedings(event.proceedings())
            .createdAt(event.occurredAt())
            .build());
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}

