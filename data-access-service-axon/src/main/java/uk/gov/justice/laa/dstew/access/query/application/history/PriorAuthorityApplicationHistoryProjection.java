package uk.gov.justice.laa.dstew.access.query.application.history;

import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.replay.annotation.ResetHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Projection that records Prior Authority assignment activity to parent Application history. */
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

  /** Records Prior Authority assignment in parent application's history. */
  @EventHandler
  public void on(WorkItemAssigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    UUID priorAuthorityId = event.workItemId();
    UUID parentApplicationId =
        priorAuthorityDataRepository
            .findFirstByPriorAuthorityId(priorAuthorityId)
            .map(pa -> pa.getApplicationId())
            .orElse(null);

    if (parentApplicationId == null) {
      return;
    }

    Object serviceName =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);

    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(parentApplicationId)
            .priorAuthorityId(priorAuthorityId)
            .priorAuthorityType(null)
            .eventType("PRIOR_AUTHORITY_ASSIGNMENT_CHANGED")
            .eventData(
                serialise(
                    Map.of(
                        "assignmentVersion", event.assignmentVersion(),
                        "caseworkerId", event.caseworkerId(),
                        "action", "ASSIGNED")))
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(event.occurredAt())
            .build());
  }

  /** Records Prior Authority unassignment in parent application's history. */
  @EventHandler
  public void on(WorkItemUnassigned event, EventMessage message) {
    if (!WorkItemType.PRIOR_AUTHORITY.equals(event.workItemType())) {
      return;
    }

    UUID priorAuthorityId = event.workItemId();
    UUID parentApplicationId =
        priorAuthorityDataRepository
            .findFirstByPriorAuthorityId(priorAuthorityId)
            .map(pa -> pa.getApplicationId())
            .orElse(null);

    if (parentApplicationId == null) {
      return;
    }

    Object serviceName =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY);

    priorAuthorityHistoryReadRepository.save(
        PriorAuthorityHistoryReadModel.builder()
            .eventId(message.identifier())
            .applicationId(parentApplicationId)
            .priorAuthorityId(priorAuthorityId)
            .priorAuthorityType(null)
            .eventType("PRIOR_AUTHORITY_ASSIGNMENT_CHANGED")
            .eventData(
                serialise(
                    Map.of("assignmentVersion", event.assignmentVersion(), "action", "UNASSIGNED")))
            .serviceName(serviceName == null ? null : serviceName.toString())
            .occurredAt(event.occurredAt())
            .build());
  }

  private String serialise(Map<String, Object> data) {
    try {
      return objectMapper.writeValueAsString(data);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialise event data", e);
    }
  }

  @ResetHandler
  public void reset() {
    // Projection can be replayed from events
  }
}
