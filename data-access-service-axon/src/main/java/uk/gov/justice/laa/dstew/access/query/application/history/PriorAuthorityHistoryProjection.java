package uk.gov.justice.laa.dstew.access.query.application.history;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataId;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.config.interceptor.RequestMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

/** Projection that records Prior Authority activity to parent Application history. */
@Component
@RequiredArgsConstructor
@Namespace("prior-authority-history-projection")
public class PriorAuthorityHistoryProjection {

  private final ObjectMapper objectMapper;
  private final PriorAuthorityDataRepository priorAuthorityDataRepository;
  private final PriorAuthorityHistoryReadRepository priorAuthorityHistoryReadRepository;

  /** Records a submitted Prior Authority in parent application's history. */
  @EventHandler
  public void on(PriorAuthoritySubmittedEvent event, EventMessage message) {
    Object serviceName =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(event.applicationId())
            .priorAuthorityId(event.priorAuthorityId())
            .priorAuthorityType(event.priorAuthorityType())
            .eventType("PRIOR_AUTHORITY_SUBMITTED")
            .itemVersion(event.dataVersion())
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(event.occurredAt())
            .build());
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
        "ASSIGN_APPLICATION_TO_CASEWORKER",
        event.itemVersion(),
        event.caseworkerId(),
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
        "UNASSIGN_APPLICATION_TO_CASEWORKER",
        event.itemVersion(),
        event.occurredAt());
  }

  private PriorAuthorityRef lookupPriorAuthorityRef(UUID priorAuthorityId) {
    return priorAuthorityDataRepository
        .findById(new PriorAuthorityDataId(priorAuthorityId, 0L))
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
      Long itemVersion,
      Instant occurredAt) {
    append(
        message,
        applicationId,
        priorAuthorityId,
        priorAuthorityType,
        eventType,
        itemVersion,
        null,
        occurredAt);
  }

  private void append(
      EventMessage message,
      UUID applicationId,
      UUID priorAuthorityId,
      String priorAuthorityType,
      String eventType,
      Long itemVersion,
      UUID caseworkerId,
      Instant occurredAt) {
    Object serviceName =
        message.metadata().get(RequestMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);
    String authenticatedUserId =
        message.metadata().get(RequestMetadataDispatchInterceptor.AUTHENTICATED_USER_ID_KEY);

    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(applicationId)
            .priorAuthorityId(priorAuthorityId)
            .priorAuthorityType(priorAuthorityType)
            .eventType(eventType)
            .itemVersion(itemVersion)
            .serviceName(serviceName == null ? null : serviceName.toString())
            .caseworkerId(
                authenticatedUserId == null ? null : UUID.fromString(authenticatedUserId))
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
