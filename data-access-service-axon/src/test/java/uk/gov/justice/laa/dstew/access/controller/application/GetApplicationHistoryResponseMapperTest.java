package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityEventResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryGroupResult;

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
        .extracting(ApplicationDomainEventResponse::getDomainEventType)
        .containsExactly(
            DomainEventType.APPLICATION_GROUP_CREATED, DomainEventType.APPLICATION_GROUP_JOINED);
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenPriorAuthorityGroup_whenMapped_thenMapsToGeneratedPriorAuthorityHistoryGroup() {
    UUID submissionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");
    var priorAuthorityGroup =
        group(submissionId, "EXPERT", event("PRIOR_AUTHORITY_CREATED", occurredAt, "CIVIL_APPLY"));

    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(priorAuthorityGroup)));

    assertThat(response.getPriorAuthorities())
        .singleElement()
        .satisfies(
            mappedGroup -> {
              assertThat(mappedGroup.getSubmissionId()).isEqualTo(submissionId);
              assertThat(mappedGroup.getPriorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
              assertThat(mappedGroup.getEvents())
                  .singleElement()
                  .satisfies(
                      mappedEvent -> {
                        assertThat(mappedEvent.getEventType()).isEqualTo("PRIOR_AUTHORITY_CREATED");
                        assertThat(mappedEvent.getCreatedBy()).isEqualTo("CIVIL_APPLY");
                        assertThat(mappedEvent.getEventDescription()).isNull();
                        assertThat(mappedEvent.getCreatedAt())
                            .isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
                      });
            });
  }

  @Test
  void givenMultiplePriorAuthorityGroups_whenMapped_thenEachGroupHasOwnEvents() {
    UUID firstSubmissionId = UUID.randomUUID();
    UUID secondSubmissionId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");

    var response =
        mapper.toResponse(
            new ApplicationHistoryResult(
                List.of(),
                List.of(
                    group(
                        firstSubmissionId,
                        "EXPERT",
                        event("PRIOR_AUTHORITY_CREATED", occurredAt, "CIVIL_APPLY")),
                    group(
                        secondSubmissionId,
                        "COUNSEL",
                        event("PRIOR_AUTHORITY_CREATED", occurredAt, "CIVIL_APPLY")))));

    assertThat(response.getPriorAuthorities())
        .extracting(group -> group.getEvents().size())
        .containsExactly(1, 1);
  }

  @Test
  void givenNoPriorAuthorityGroups_whenMapped_thenReturnsEmptyList() {
    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of()));
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenNullServiceName_whenMapped_thenCreatedByIsUnknown() {
    UUID submissionId = UUID.randomUUID();
    var priorAuthorityGroup =
        group(
            submissionId,
            "EXPERT",
            new PriorAuthorityHistoryEventResult(
                "PRIOR_AUTHORITY_CREATED", Instant.parse("2026-08-05T10:00:00Z"), null, null));

    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(priorAuthorityGroup)));

    assertThat(response.getPriorAuthorities())
        .singleElement()
        .satisfies(
            group ->
                assertThat(group.getEvents())
                    .extracting(PriorAuthorityEventResponse::getCreatedBy)
                    .containsExactly("UNKNOWN"));
  }

  private PriorAuthorityHistoryGroupResult group(
      UUID submissionId, String priorAuthorityType, PriorAuthorityHistoryEventResult... events) {
    return new PriorAuthorityHistoryGroupResult(submissionId, priorAuthorityType, List.of(events));
  }

  private PriorAuthorityHistoryEventResult event(
      String eventType, Instant occurredAt, String serviceName) {
    return new PriorAuthorityHistoryEventResult(eventType, occurredAt, serviceName, null);
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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

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

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(history), List.of()));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(event -> assertThat(event.getCaseworkerId()).isEqualTo(caseworkerId));
  }
}
