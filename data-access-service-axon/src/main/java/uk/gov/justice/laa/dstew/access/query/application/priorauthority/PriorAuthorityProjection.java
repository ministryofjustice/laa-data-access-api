package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;

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
    createRow(
        event.submissionId(),
        event.applicationId(),
        event.dataVersion(),
        event.status(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  /** Creates the current-state row once a prior-authority draft has been submitted. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
    createRow(
        event.submissionId(),
        event.applicationId(),
        event.dataVersion(),
        event.status(),
        event.occurredAt(),
        queryUpdateEmitter);
  }

  private void createRow(
      UUID submissionId,
      UUID applicationId,
      long dataVersion,
      String status,
      Instant createdAt,
      QueryUpdateEmitter queryUpdateEmitter) {
    PriorAuthorityReadModel saved =
        repository.save(
            PriorAuthorityReadModel.builder()
                .submissionId(submissionId)
                .applicationId(applicationId)
                .dataVersion(dataVersion)
                .status(status)
                .createdAt(createdAt)
                .build());
    queryUpdateEmitter.emit(
        FindPriorAuthorityBySubmissionIdQuery.class,
        query -> query.submissionId().equals(submissionId),
        saved);
  }

  /** Clears the disposable current-state table before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }
}
