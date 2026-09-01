package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.Optional;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;

/** Independently replayable projection of the current state of each prior-authority submission. */
@Component
@Namespace("prior-authority-projection")
public class PriorAuthorityProjection {

  private final PriorAuthorityReadRepository repository;

  public PriorAuthorityProjection(PriorAuthorityReadRepository repository) {
    this.repository = repository;
  }

  /** Returns the current-state projection for the requested prior-authority submission. */
  @QueryHandler
  public Optional<PriorAuthorityReadModel> handle(FindPriorAuthorityBySubmissionIdQuery query) {
    return repository.findById(query.submissionId());
  }

  /** Creates the current-state row from a prior-authority creation event. */
  @EventHandler
  public void on(PriorAuthorityCreatedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    PriorAuthorityReadModel saved =
        repository.save(
            PriorAuthorityReadModel.builder()
                .submissionId(event.submissionId())
                .applicationId(event.applicationId())
                .dataVersion(event.dataVersion())
                .status(event.status())
                .createdAt(event.occurredAt())
                .build());
    queryUpdateEmitter.emit(
        FindPriorAuthorityBySubmissionIdQuery.class,
        query -> query.submissionId().equals(event.submissionId()),
        saved);
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
