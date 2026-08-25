package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
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
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryReadModel;

/** Maps the Axon application-history projection to the shared HTTP response contract. */
@Component
public class GetApplicationHistoryResponseMapper {

  private final ObjectMapper objectMapper;

  public GetApplicationHistoryResponseMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps history rows in their repository-provided chronological order. */
  public ApplicationHistoryResponse toResponse(ApplicationHistoryResult result) {
    var events = result.applicationEvents().stream().map(this::toEvent).toList();
    var paGroups = toPaGroups(result.priorAuthorityEvents());
    return ApplicationHistoryResponse.builder().events(events).priorAuthorities(paGroups).build();
  }

  private List<PriorAuthorityHistoryGroup> toPaGroups(List<PriorAuthorityHistoryReadModel> rows) {
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                PriorAuthorityHistoryReadModel::getSubmissionId,
                LinkedHashMap::new,
                Collectors.toList()))
        .entrySet()
        .stream()
        .map(
            entry -> {
              List<PriorAuthorityHistoryReadModel> groupRows = entry.getValue();
              return PriorAuthorityHistoryGroup.builder()
                  .submissionId(entry.getKey())
                  .priorAuthorityType(
                      PriorAuthorityType.fromValue(groupRows.getFirst().getPriorAuthorityType()))
                  .events(
                      groupRows.stream()
                          .sorted(
                              Comparator.comparing(PriorAuthorityHistoryReadModel::getOccurredAt))
                          .map(this::toPaEvent)
                          .toList())
                  .build();
            })
        .toList();
  }

  private PriorAuthorityEventResponse toPaEvent(PriorAuthorityHistoryReadModel row) {
    return PriorAuthorityEventResponse.builder()
        .eventType(row.getEventType())
        .createdAt(row.getOccurredAt().atOffset(ZoneOffset.UTC))
        .createdBy(row.getServiceName() == null ? "UNKNOWN" : row.getServiceName())
        .eventDescription(null)
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

  private java.util.UUID caseworkerId(String requestPayload) {
    if (requestPayload == null || requestPayload.isBlank()) {
      return null;
    }
    try {
      JsonNode id = objectMapper.readTree(requestPayload).get("caseworkerId");
      return id == null || id.isNull() ? null : java.util.UUID.fromString(id.asText());
    } catch (Exception exception) {
      return null;
    }
  }
}
