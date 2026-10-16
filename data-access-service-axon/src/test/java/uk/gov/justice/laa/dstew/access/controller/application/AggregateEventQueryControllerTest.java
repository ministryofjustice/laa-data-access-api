package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.usecase.application.AggregateEventHistoryUseCase;

class AggregateEventQueryControllerTest {

  static Stream<Map<String, String>> metadata() {
    return Stream.of(Map.of(), Map.of("arbitrary-key", "internal value", "trace.id", "123"));
  }

  @ParameterizedTest
  @MethodSource("metadata")
  void preservesAllMetadataAndThinPayloadForBothEndpoints(Map<String, String> metadata) {
    var useCase = mock(AggregateEventHistoryUseCase.class);
    var controller = new AggregateEventQueryController(useCase, JsonMapper.builder().build());
    UUID id = UUID.randomUUID();
    Instant timestamp = Instant.parse("2026-10-16T12:00:00Z");
    var payload = Map.of("aggregateId", id.toString(), "dataVersion", 2);
    var event =
        new GenericEventMessage(
            "event-id", new MessageType("unfamiliar.event"), payload, metadata, timestamp);
    when(useCase.applicationEvents(id)).thenReturn(List.of(event));
    when(useCase.priorAuthorityEvents(id)).thenReturn(List.of(event));

    var application = controller.getApplicationEvents(null, id).getBody();
    var priorAuthority = controller.getPriorAuthorityEvents(null, id).getBody();

    assertThat(application).hasSize(1);
    var response = application.getFirst();
    assertThat(response.getEventId()).isEqualTo("event-id");
    assertThat(response.getEventType()).isEqualTo("unfamiliar.event");
    assertThat(response.getTimestamp().toInstant()).isEqualTo(timestamp);
    assertThat(response.getMetadata()).isEqualTo(metadata);
    assertThat(response.getPayload()).isEqualTo(payload);
    assertThat(priorAuthority).isEqualTo(application);
  }
}
