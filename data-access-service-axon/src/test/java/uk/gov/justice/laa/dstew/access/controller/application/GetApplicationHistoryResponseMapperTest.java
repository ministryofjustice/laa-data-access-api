package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.model.ApplicationDomainEventResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityEventResponse;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryEventResult;
import uk.gov.justice.laa.dstew.access.query.application.history.PriorAuthorityHistoryGroupResult;

class GetApplicationHistoryResponseMapperTest {

  private final GetApplicationHistoryResponseMapper mapper =
      new GetApplicationHistoryResponseMapper();

  @Test
  void givenHistoryRows_whenMapped_thenReturnsSharedApplicationHistoryContract() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:30Z");
    var event =
        new ApplicationHistoryEventResult(
            applicationId,
            "APPLICATION_NOTE_CREATED",
            occurredAt,
            "CIVIL_APPLY",
            "My note text",
            caseworkerId);

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(event), List.of()));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            mappedEvent -> {
              assertThat(mappedEvent.getApplicationId()).isEqualTo(applicationId);
              assertThat(mappedEvent.getDomainEventType())
                  .isEqualTo(DomainEventType.APPLICATION_NOTE_CREATED);
              assertThat(mappedEvent.getCreatedAt())
                  .isEqualTo(OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
              assertThat(mappedEvent.getCreatedBy()).isEqualTo("CIVIL_APPLY");
              assertThat(mappedEvent.getEventDescription()).isEqualTo("My note text");
              assertThat(mappedEvent.getCaseworkerId()).isEqualTo(caseworkerId);
            });
    assertThat(response.getPriorAuthorities()).isEmpty();
  }

  @Test
  void givenNullServiceNameAndDescription_whenMapped_thenUsesSafeFallbacks() {
    var event =
        new ApplicationHistoryEventResult(
            UUID.randomUUID(),
            "APPLICATION_CREATED",
            Instant.parse("2026-07-19T10:15:30Z"),
            null,
            null,
            null);

    var response = mapper.toResponse(new ApplicationHistoryResult(List.of(event), List.of()));

    assertThat(response.getEvents())
        .singleElement()
        .satisfies(
            mappedEvent -> {
              assertThat(mappedEvent.getCreatedBy()).isEqualTo("UNKNOWN");
              assertThat(mappedEvent.getEventDescription()).isNull();
              assertThat(mappedEvent.getCaseworkerId()).isNull();
            });
  }

  @Test
  void givenGroupHistoryRows_whenMapped_thenReturnsGroupDomainEventTypes() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:15:30Z");
    var created =
        new ApplicationHistoryEventResult(
            applicationId, "APPLICATION_GROUP_CREATED", occurredAt, "CIVIL_APPLY", null, null);
    var joined =
        new ApplicationHistoryEventResult(
            applicationId, "APPLICATION_GROUP_JOINED", occurredAt, "CIVIL_APPLY", null, null);

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
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");
    var priorAuthorityGroup =
        group(
            priorAuthorityId,
            "EXPERT",
            event("PRIOR_AUTHORITY_SUBMITTED", occurredAt, "CIVIL_APPLY"));

    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(priorAuthorityGroup)));

    assertThat(response.getPriorAuthorities())
        .singleElement()
        .satisfies(
            mappedGroup -> {
              assertThat(mappedGroup.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
              assertThat(mappedGroup.getPriorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT);
              assertThat(mappedGroup.getEvents())
                  .singleElement()
                  .satisfies(
                      mappedEvent -> {
                        assertThat(mappedEvent.getEventType())
                            .isEqualTo("PRIOR_AUTHORITY_SUBMITTED");
                        assertThat(mappedEvent.getCreatedBy()).isEqualTo("CIVIL_APPLY");
                        assertThat(mappedEvent.getEventDescription()).isNull();
                        assertThat(mappedEvent.getCaseworkerId()).isNull();
                        assertThat(mappedEvent.getCreatedAt())
                            .isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
                      });
            });
  }

  @Test
  void givenMultiplePriorAuthorityGroups_whenMapped_thenEachGroupHasOwnEvents() {
    UUID firstPriorAuthorityId = UUID.randomUUID();
    UUID secondPriorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");

    var response =
        mapper.toResponse(
            new ApplicationHistoryResult(
                List.of(),
                List.of(
                    group(
                        firstPriorAuthorityId,
                        "EXPERT",
                        event("PRIOR_AUTHORITY_SUBMITTED", occurredAt, "CIVIL_APPLY")),
                    group(
                        secondPriorAuthorityId,
                        "COUNSEL",
                        event("PRIOR_AUTHORITY_SUBMITTED", occurredAt, "CIVIL_APPLY")))));

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
  void givenNullPriorAuthorityServiceName_whenMapped_thenCreatedByIsUnknown() {
    UUID priorAuthorityId = UUID.randomUUID();
    var priorAuthorityGroup =
        group(
            priorAuthorityId,
            "EXPERT",
            new PriorAuthorityHistoryEventResult(
                "PRIOR_AUTHORITY_SUBMITTED",
                Instant.parse("2026-08-05T10:00:00Z"),
                null,
                null,
                null));

    var response =
        mapper.toResponse(new ApplicationHistoryResult(List.of(), List.of(priorAuthorityGroup)));

    assertThat(response.getPriorAuthorities())
        .singleElement()
        .satisfies(
            mappedGroup ->
                assertThat(mappedGroup.getEvents())
                    .extracting(PriorAuthorityEventResponse::getCreatedBy)
                    .containsExactly("UNKNOWN"));
  }

  private PriorAuthorityHistoryGroupResult group(
      UUID priorAuthorityId,
      String priorAuthorityType,
      PriorAuthorityHistoryEventResult... events) {
    return new PriorAuthorityHistoryGroupResult(
        priorAuthorityId, priorAuthorityType, List.of(events));
  }

  private PriorAuthorityHistoryEventResult event(
      String eventType, Instant occurredAt, String serviceName) {
    return new PriorAuthorityHistoryEventResult(eventType, occurredAt, serviceName, null, null);
  }
}
