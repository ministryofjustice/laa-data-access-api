package uk.gov.justice.laa.dstew.access.query.submission;

import java.util.Optional;
import java.util.UUID;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ResetHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationSubmittedEvent;

/**
 * Stores the raw submitted payload for each application in the {@code submissions} table, keyed by
 * the producing event. Runs in the synchronous (subscribing) projection group so the payload is
 * available for the GET response immediately after a create.
 */
@Component
@ProcessingGroup("application-projection")
public class SubmissionProjection {

  private final SubmissionRepository repository;

  public SubmissionProjection(SubmissionRepository repository) {
    this.repository = repository;
  }

  /** Returns the raw submission payload for the requested application. */
  @QueryHandler
  public Optional<SubmissionData> handle(FindSubmissionByApplicationIdQuery query) {
    return repository
        .findFirstByApplyApplicationIdOrderByCreatedAtDesc(query.applyApplicationId())
        .map(SubmissionRecord::getData);
  }

  /** Persists the submitted payload alongside its causation and correlation ids. */
  @EventHandler
  public void on(ApplicationSubmittedEvent event, EventMessage<?> message) {
    MetaData metaData = message.getMetaData();
    repository.save(
        SubmissionRecord.builder()
            .eventId(UUID.fromString(message.getIdentifier()))
            .applyApplicationId(event.applyApplicationId())
            .submissionType(SubmissionType.CIVIL_APPLICATION)
            .causationId(asUuid(metaData.get("traceId")))
            .correlationId(asUuid(metaData.get("correlationId")))
            .data(
                new SubmissionData(
                    event.applicationContent(), event.individuals(), event.proceedings()))
            .createdAt(event.occurredAt())
            .build());
  }

  /** Clears the disposable payload store before replay. */
  @ResetHandler
  public void reset() {
    repository.deleteAllInBatch();
  }

  private static UUID asUuid(Object value) {
    return value == null ? null : UUID.fromString(value.toString());
  }
}
