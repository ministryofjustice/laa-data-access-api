package uk.gov.justice.laa.dstew.access.controller.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;

/** Maps the Axon application-history projection to the shared HTTP response contract. */
@Component
public class GetApplicationHistoryResponseMapper {

  private final ObjectMapper objectMapper;

  public GetApplicationHistoryResponseMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps history rows in their repository-provided chronological order. */
  public ApplicationHistoryResponse toResponse(List<ApplicationHistoryReadModel> history) {
    List<ApplicationDomainEventResponse> events = history.stream().map(this::toEvent).toList();
    return ApplicationHistoryResponse.builder().events(events).build();
  }

  private ApplicationDomainEventResponse toEvent(ApplicationHistoryReadModel history) {
    return ApplicationDomainEventResponse.builder()
        .applicationId(history.getApplicationId())
        .domainEventType(DomainEventType.fromValue(history.getEventType()))
        .createdAt(history.getOccurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(history.getServiceName() == null ? "UNKNOWN" : history.getServiceName())
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
}
