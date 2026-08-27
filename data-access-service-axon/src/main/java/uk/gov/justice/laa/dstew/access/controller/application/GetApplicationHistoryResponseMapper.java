package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityEventResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityHistoryGroup;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryGroupResult;

/** Maps the Axon application-history projection to the shared HTTP response contract. */
@Component
public class GetApplicationHistoryResponseMapper {

  private final ObjectMapper objectMapper;

  public GetApplicationHistoryResponseMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps history rows in their repository-provided chronological order. */
  public ApplicationHistoryResponse toResponse(ApplicationHistoryResult result) {
    var applicationEvents = result.applicationEvents().stream().map(this::toEvent).toList();
    var priorAuthorityGroups = result.priorAuthorities().stream().map(this::toPaGroup).toList();
    return ApplicationHistoryResponse.builder()
        .events(applicationEvents)
        .priorAuthorities(priorAuthorityGroups)
        .build();
  }

  private PriorAuthorityHistoryGroup toPaGroup(PriorAuthorityHistoryGroupResult group) {
    return PriorAuthorityHistoryGroup.builder()
        .submissionId(group.submissionId())
        .priorAuthorityType(PriorAuthorityType.fromValue(group.priorAuthorityType()))
        .events(group.events().stream().map(this::toPaEvent).toList())
        .build();
  }

  private PriorAuthorityEventResponse toPaEvent(PriorAuthorityHistoryEventResult event) {
    return PriorAuthorityEventResponse.builder()
        .eventType(event.eventType())
        .createdAt(event.occurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(event.serviceName() == null ? "UNKNOWN" : event.serviceName())
        .eventDescription(event.eventDescription())
        .build();
  }

  private ApplicationDomainEventResponse toEvent(ApplicationHistoryReadModel history) {
    return ApplicationDomainEventResponse.builder()
        .applicationId(history.getApplicationId())
        .domainEventType(DomainEventType.fromValue(history.getEventType()))
        .createdAt(history.getOccurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(history.getServiceName() == null ? "UNKNOWN" : history.getServiceName())
        .caseworkerId(caseworkerId(history.getRequestPayload()))
        .eventDescription(eventDescription(history.getRequestPayload()))
        .build();
  }

  private String eventDescription(String requestPayload) {
    if (requestPayload == null || requestPayload.isBlank()) {
      return null;
    }
    try {
      JsonNode description = objectMapper.readTree(requestPayload).get("eventDescription");
      return description == null || description.isNull() ? null : description.asText();
    } catch (Exception exception) {
      return null;
    }
  }

  private UUID caseworkerId(String requestPayload) {
    if (requestPayload == null || requestPayload.isBlank()) {
      return null;
    }
    try {
      JsonNode caseworkerIdNode = objectMapper.readTree(requestPayload).get("caseworkerId");
      return caseworkerIdNode == null || caseworkerIdNode.isNull()
          ? null
          : UUID.fromString(caseworkerIdNode.asText());
    } catch (Exception exception) {
      return null;
    }
  }
}
