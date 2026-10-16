package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.api.AggregateEventQueryApi;
import uk.gov.justice.laa.dstew.access.model.AggregateEventResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.usecase.application.AggregateEventHistoryUseCase;

/** HTTP adapter for complete, thin aggregate event history. */
@RestController
@RequiredArgsConstructor
public class AggregateEventQueryController implements AggregateEventQueryApi {

  private final AggregateEventHistoryUseCase useCase;
  private final ObjectMapper objectMapper;

  @Override
  public ResponseEntity<List<AggregateEventResponse>> getApplicationEvents(
      ServiceName serviceName, UUID id) {
    return ResponseEntity.ok(map(useCase.applicationEvents(id)));
  }

  @Override
  public ResponseEntity<List<AggregateEventResponse>> getPriorAuthorityEvents(
      ServiceName serviceName, UUID priorAuthorityId) {
    return ResponseEntity.ok(map(useCase.priorAuthorityEvents(priorAuthorityId)));
  }

  private List<AggregateEventResponse> map(List<EventMessage> events) {
    return events.stream()
        .map(
            event ->
                new AggregateEventResponse()
                    .eventId(event.identifier())
                    .eventType(event.type().name())
                    .timestamp(event.timestamp().atOffset(ZoneOffset.UTC))
                    .metadata(new LinkedHashMap<>(event.metadata()))
                    .payload(
                        objectMapper.convertValue(
                            event.payloadAs(Map.class), new TypeReference<>() {})))
        .toList();
  }
}
