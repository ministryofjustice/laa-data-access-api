package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;

class GetApplicationHistoryResponseMapperTest {

  private final GetApplicationHistoryResponseMapper mapper =
      new GetApplicationHistoryResponseMapper(new ObjectMapper());

  @Test
  void givenHistoryRows_whenMapped_thenReturnsSharedApplicationHistoryContract() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:30Z");
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(applicationId)
            .eventType("APPLICATION_CREATED")
            .requestPayload("{\"eventDescription\":\"Application received\"}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(occurredAt)
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getApplicationId()).isEqualTo(applicationId);
              assertThat(event.getDomainEventType()).isEqualTo(DomainEventType.APPLICATION_CREATED);
              assertThat(event.getCreatedAt())
                  .isEqualTo(OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
              assertThat(event.getCreatedBy()).isEqualTo("CIVIL_APPLY");
              assertThat(event.getEventDescription()).isEqualTo("Application received");
              assertThat(event.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenMissingMetadataAndInvalidPayload_whenMapped_thenUsesSafeFallbacks() {
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(UUID.randomUUID())
            .eventType("APPLICATION_CREATED")
            .requestPayload("not-json")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getCreatedBy()).isEqualTo("UNKNOWN");
              assertThat(event.getEventDescription()).isNull();
            });
  }

  @Test
  void givenGroupHistoryRows_whenMapped_thenReturnsGroupDomainEventTypes() {
    UUID applicationId = UUID.randomUUID();
    ApplicationHistoryReadModel created =
        ApplicationHistoryReadModel.builder()
            .eventId("group-created")
            .applicationId(applicationId)
            .eventType("APPLICATION_GROUP_CREATED")
            .requestPayload("{}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();
    ApplicationHistoryReadModel joined =
        ApplicationHistoryReadModel.builder()
            .eventId("group-joined")
            .applicationId(applicationId)
            .eventType("APPLICATION_GROUP_JOINED")
            .requestPayload("{}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:16:30Z"))
            .build();

    var response = mapper.toResponse(List.of(created, joined));

    assertThat(response.getEvents())
        .extracting(event -> event.getDomainEventType())
        .containsExactly(
            DomainEventType.APPLICATION_GROUP_CREATED, DomainEventType.APPLICATION_GROUP_JOINED);
  }
}
