package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityEventResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryReadModel;

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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

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
    assertThat(response.getPriorAuthorities()).isEmpty();
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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getCreatedBy()).isEqualTo("UNKNOWN");
              assertThat(event.getEventDescription()).isNull();
            });
    assertThat(response.getPriorAuthorities()).isEmpty();
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

    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(created, joined), List.of()));

    assertThat(response.getEvents())
        .extracting(event -> event.getDomainEventType())
        .containsExactly(
            DomainEventType.APPLICATION_GROUP_CREATED, DomainEventType.APPLICATION_GROUP_JOINED);
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenPriorAuthorityHistory_whenMapped_thenGroupsBySubmissionId() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    var row =
        PriorAuthorityHistoryReadModel.builder()
            .eventId("evt-1")
            .applicationId(applicationId)
            .submissionId(submissionId)
            .priorAuthorityType("EXPERT")
            .eventType("PRIOR_AUTHORITY_CREATED")
            .eventData("{\"status\":\"PENDING\",\"dataVersion\":0}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-08-05T10:00:00Z"))
            .build();

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(row)));

    assertThat(response.getPriorAuthorities()).hasSize(1);
    var group = response.getPriorAuthorities().get(0);
    assertThat(group.getSubmissionId()).isEqualTo(submissionId);
    assertThat(group.getPriorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
    assertThat(group.getEvents()).hasSize(1);
    assertThat(group.getEvents().get(0).getEventType()).isEqualTo("PRIOR_AUTHORITY_CREATED");
    assertThat(group.getEvents().get(0).getCreatedBy()).isEqualTo("CIVIL_APPLY");
    assertThat(group.getEvents().get(0).getEventDescription()).isNull();
  }

  @Test
  void givenMultiplePriorAuthorities_whenMapped_thenEachGroupHasOwnEvents() {
    UUID appId = UUID.randomUUID();
    UUID sub1 = UUID.randomUUID();
    UUID sub2 = UUID.randomUUID();
    var row1 = paRow(appId, sub1, "EXPERT");
    var row2 = paRow(appId, sub2, "COUNSEL");

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(row1, row2)));

    assertThat(response.getPriorAuthorities()).hasSize(2);
    assertThat(response.getPriorAuthorities().get(0).getEvents()).hasSize(1);
    assertThat(response.getPriorAuthorities().get(1).getEvents()).hasSize(1);
  }

  @Test
  void givenNoPriorAuthorities_whenMapped_thenReturnsEmptyList() {
    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of()));
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenMultipleEventsForOnePa_whenMapped_thenEventsAreChronologicallyOrdered() {
    UUID appId = UUID.randomUUID();
    UUID sub = UUID.randomUUID();
    var earlier = paRowAt(appId, sub, "EXPERT", Instant.parse("2026-08-05T09:00:00Z"));
    var later = paRowAt(appId, sub, "EXPERT", Instant.parse("2026-08-05T11:00:00Z"));
    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(later, earlier)));

    assertThat(response.getPriorAuthorities()).hasSize(1);
    List<PriorAuthorityEventResponse> events = response.getPriorAuthorities().get(0).getEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0).getCreatedAt()).isBefore(events.get(1).getCreatedAt());
  }

  @Test
  void givenNullServiceName_whenMapped_thenCreatedByIsUnknown() {
    UUID appId = UUID.randomUUID();
    UUID sub = UUID.randomUUID();
    var row =
        PriorAuthorityHistoryReadModel.builder()
            .eventId("e1")
            .applicationId(appId)
            .submissionId(sub)
            .priorAuthorityType("EXPERT")
            .eventType("PRIOR_AUTHORITY_CREATED")
            .eventData("{}")
            .serviceName(null)
            .occurredAt(Instant.parse("2026-08-05T10:00:00Z"))
            .build();

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(row)));

    assertThat(response.getPriorAuthorities().get(0).getEvents().get(0).getCreatedBy())
        .isEqualTo("UNKNOWN");
  }

  private PriorAuthorityHistoryReadModel paRow(UUID appId, UUID submissionId, String type) {
    return paRowAt(appId, submissionId, type, Instant.parse("2026-08-05T10:00:00Z"));
  }

  private PriorAuthorityHistoryReadModel paRowAt(
      UUID appId, UUID submissionId, String type, Instant occurredAt) {
    return PriorAuthorityHistoryReadModel.builder()
        .eventId(UUID.randomUUID().toString())
        .applicationId(appId)
        .submissionId(submissionId)
        .priorAuthorityType(type)
        .eventType("PRIOR_AUTHORITY_CREATED")
        .eventData("{\"status\":\"PENDING\",\"dataVersion\":0}")
        .serviceName("CIVIL_APPLY")
        .occurredAt(occurredAt)
        .build();
  }

  @Test
  void givenNullPayload_whenMapped_thenEventDescriptionAndCaseworkerIdAreNull() {
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(UUID.randomUUID())
            .eventType("APPLICATION_CREATED")
            .requestPayload(null)
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventDescription()).isNull();
              assertThat(event.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenBlankPayload_whenMapped_thenEventDescriptionAndCaseworkerIdAreNull() {
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(UUID.randomUUID())
            .eventType("APPLICATION_CREATED")
            .requestPayload("   ")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventDescription()).isNull();
              assertThat(event.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenPayloadWithJsonNullFields_whenMapped_thenEventDescriptionAndCaseworkerIdAreNull() {
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(UUID.randomUUID())
            .eventType("APPLICATION_CREATED")
            .requestPayload("{\"eventDescription\":null,\"caseworkerId\":null}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventDescription()).isNull();
              assertThat(event.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenPayloadWithValidCaseworkerId_whenMapped_thenCaseworkerIdIsMapped() {
    UUID caseworkerId = UUID.randomUUID();
    ApplicationHistoryReadModel history =
        ApplicationHistoryReadModel.builder()
            .eventId("event-id")
            .applicationId(UUID.randomUUID())
            .eventType("APPLICATION_CREATED")
            .requestPayload("{\"caseworkerId\":\"" + caseworkerId + "\"}")
            .serviceName("CIVIL_APPLY")
            .occurredAt(Instant.parse("2026-07-19T10:15:30Z"))
            .build();

    var response = mapper.toResponse(List.of(history));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(event -> assertThat(event.getCaseworkerId()).isEqualTo(caseworkerId));
  }
}
