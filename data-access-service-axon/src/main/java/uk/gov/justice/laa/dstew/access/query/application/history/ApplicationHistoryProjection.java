package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ResetHandler;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationLinkedEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Independently replayable, append-only audit projection of Application events. */
@Component
@ProcessingGroup("application-history-projection")
public class ApplicationHistoryProjection {

  private final ApplicationHistoryReadRepository applicationHistoryReadRepository;

  public ApplicationHistoryProjection(
      ApplicationHistoryReadRepository applicationHistoryReadRepository) {
    this.applicationHistoryReadRepository = applicationHistoryReadRepository;
  }

  /**
   * Appends an audit entry when an Application is created.
   *
   * @param event the Application creation event
   * @param message the Axon message carrying event metadata
   */
  @EventHandler
  public void on(ApplicationCreatedEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_CREATED",
        event.serialisedRequest(),
        event.occurredAt());
  }

  /**
   * Appends an audit entry when an Application is linked.
   *
   * @param event the Application linking event
   * @param message the Axon message carrying event metadata
   */
  @EventHandler
  public void on(ApplicationLinkedEvent event, EventMessage<?> message) {
    append(
        message,
        event.applicationId(),
        "APPLICATION_LINKED",
        event.serialisedRequest(),
        event.occurredAt());
  }

  @ResetHandler
  public void reset() {
    applicationHistoryReadRepository.deleteAllInBatch();
  }

  private void append(
      EventMessage<?> message,
      UUID applicationId,
      String eventType,
      String requestPayload,
      Instant occurredAt) {
    Object serviceName =
        message.getMetaData().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    applicationHistoryReadRepository.save(
        ApplicationHistoryReadModel.builder()
            .eventId(message.getIdentifier())
            .applicationId(applicationId)
            .eventType(eventType)
            .requestPayload(requestPayload)
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(occurredAt)
            .build());
  }
}
