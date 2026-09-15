package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

/** Projection that records Prior Authority activity to parent Application history. */
@Component
@Namespace("prior-authority-application-history-projection")
public class PriorAuthorityApplicationHistoryProjection {

  private final PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository;
  private final PriorAuthorityDataRepository priorAuthorityDataRepository;
  private final ObjectMapper objectMapper;

  /** Creates the projection with its persistence and data lookup dependencies. */
  public PriorAuthorityApplicationHistoryProjection(
      PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository,
      PriorAuthorityDataRepository priorAuthorityDataRepository,
      ObjectMapper objectMapper) {
    this.priorAuthorityHistoryReadRepository = priorAuthorityHistoryReadRepository;
    this.priorAuthorityDataRepository = priorAuthorityDataRepository;
    this.objectMapper = objectMapper;
  }

  /** Records a submitted Prior Authority in parent application's history. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    append(
        message,
        event.applicationId(),
        event.priorAuthorityId(),
        event.priorAuthorityType(),
        "PRIOR_AUTHORITY_SUBMITTED",
        serialise(event),
        event.occurredAt());
  }

  /** Records Prior Authority assignment in parent application's history. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    PriorAuthorityRef ref = lookupPriorAuthorityRef(event.workItemId());
    if (ref == null) {
      return;
    }

    append(
        message,
        ref.applicationId(),
        event.workItemId(),
        ref.priorAuthorityType(),
        "PRIOR_AUTHORITY_ASSIGNMENT_CHANGED",
        serialise(event),
        event.occurredAt());
  }

  /** Records Prior Authority unassignment in parent application's history. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    PriorAuthorityRef ref = lookupPriorAuthorityRef(event.workItemId());
    if (ref == null) {
      return;
    }

    append(
        message,
        ref.applicationId(),
        event.workItemId(),
        ref.priorAuthorityType(),
        "PRIOR_AUTHORITY_ASSIGNMENT_CHANGED",
        serialise(event),
        event.occurredAt());
  }

  private PriorAuthorityRef lookupPriorAuthorityRef(UUID priorAuthorityId) {
    return priorAuthorityDataRepository
        .findFirstByPriorAuthorityId(priorAuthorityId)
        .map(
            pa -> {
              PriorAuthorityType type = pa.getPayload().content().priorAuthorityType();
              return new PriorAuthorityRef(
                  pa.getApplicationId(), type == null ? null : type.name());
            })
        .orElse(null);
  }

  private record PriorAuthorityRef(UUID applicationId, String priorAuthorityType) {}

  private void append(
      EventMessage message,
      UUID applicationId,
      UUID priorAuthorityId,
      String priorAuthorityType,
      String eventType,
      String eventData,
      Instant occurredAt) {
    Object serviceName =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(applicationId)
            .priorAuthorityId(priorAuthorityId)
            .priorAuthorityType(priorAuthorityType)
            .eventType(eventType)
            .eventData(eventData)
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(occurredAt)
            .build());
  }

  private String serialise(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Failed to serialise event data", exception);
    }
  }

  @ResetHandler
  public void reset() {
    priorAuthorityHistoryReadRepository.deleteAllInBatch();
  }
}
