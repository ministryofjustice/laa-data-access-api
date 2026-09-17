package uk.gov.justice.laa.dstew.access.query.application.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataRepository;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityApplicationHistoryProjectionTest {

  @Mock private PriorAuthorityHistoryReadRepository paRepository;

  @Mock private PriorAuthorityDataRepository paDataRepository;

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  private PriorAuthorityApplicationHistoryProjection projection;

  @BeforeEach
  void setUp() {
    projection =
        new PriorAuthorityApplicationHistoryProjection(
            paRepository, paDataRepository, objectMapper);
  }

  @Test
  void givenPriorAuthoritySubmittedEvent_whenHandled_thenStoresInPaHistoryTable() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");
    var event =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 0L, 0L, occurredAt);
    var msg = message(event, "pa-submit-event-id");

    projection.on(event, msg);

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getEventId()).isEqualTo("pa-submit-event-id");
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(saved.getEventType()).isEqualTo("PRIOR_AUTHORITY_SUBMITTED");
    assertThat(saved.getServiceName()).isEqualTo("CIVIL_APPLY");
    assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(saved.getEventData()).contains("\"status\":\"SUBMITTED\"");
    assertThat(saved.getEventData()).contains("\"dataVersion\":0");
  }

  @Test
  void givenPriorAuthoritySubmittedEventWithoutServiceName_whenHandled_thenStoresNullServiceName() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T10:00:00Z");
    var event =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 0L, 0L, occurredAt);

    projection.on(event, messageWithoutServiceName(event, "pa-submit-event-id"));

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    assertThat(captor.getValue().getServiceName()).isNull();
  }

  @Test
  void givenPriorAuthorityWorkItemAssigned_whenHandled_thenStoresInPaHistoryTable()
      throws Exception {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T11:00:00Z");
    WorkItemAssigned event =
        new WorkItemAssigned(
            priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, occurredAt);
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(
            Optional.of(paData(priorAuthorityId, applicationId, PriorAuthorityType.EXPERT)));

    projection.on(event, message(event, "pa-assign-event-id"));

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getEventId()).isEqualTo("pa-assign-event-id");
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(saved.getEventType()).isEqualTo("ASSIGN_APPLICATION_TO_CASEWORKER");
    assertThat(saved.getServiceName()).isEqualTo("CIVIL_APPLY");
    assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    var payload = objectMapper.readTree(saved.getEventData());
    assertThat(payload.get("caseworkerId").asString()).isEqualTo(caseworkerId.toString());
    assertThat(payload.get("workItemType").asString()).isEqualTo("PRIOR_AUTHORITY");
  }

  @Test
  void
      givenPriorAuthorityWorkItemAssignedWithNoPriorAuthorityType_whenHandled_thenStoresNullType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    WorkItemAssigned event =
        new WorkItemAssigned(
            priorAuthorityId,
            WorkItemType.PRIOR_AUTHORITY,
            1L,
            1L,
            UUID.randomUUID(),
            Instant.parse("2026-08-05T11:00:00Z"));
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(Optional.of(paData(priorAuthorityId, applicationId, null)));

    projection.on(event, message(event, "pa-assign-event-id"));

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    assertThat(captor.getValue().getPriorAuthorityType()).isNull();
  }

  @Test
  void
      givenPriorAuthorityWorkItemAssignedWithoutServiceName_whenHandled_thenStoresNullServiceName() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    WorkItemAssigned event =
        new WorkItemAssigned(
            priorAuthorityId,
            WorkItemType.PRIOR_AUTHORITY,
            1L,
            1L,
            UUID.randomUUID(),
            Instant.parse("2026-08-05T11:00:00Z"));
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(
            Optional.of(paData(priorAuthorityId, applicationId, PriorAuthorityType.EXPERT)));

    projection.on(event, messageWithoutServiceName(event, "pa-assign-event-id"));

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    assertThat(captor.getValue().getServiceName()).isNull();
  }

  @Test
  void givenApplicationWorkItemAssigned_whenHandled_thenIgnored() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T11:00:00Z");
    WorkItemAssigned event =
        new WorkItemAssigned(
            applicationId, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, occurredAt);

    projection.on(event, message(event, "app-assign-event-id"));

    verify(paRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(paDataRepository, never())
        .findFirstByPriorAuthorityId(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenPriorAuthorityWorkItemAssignedButNoDataFound_whenHandled_thenNothingSaved() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T11:00:00Z");
    WorkItemAssigned event =
        new WorkItemAssigned(
            priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, occurredAt);
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(Optional.empty());

    projection.on(event, message(event, "pa-assign-event-id"));

    verify(paRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenPriorAuthorityWorkItemUnassigned_whenHandled_thenStoresInPaHistoryTable()
      throws Exception {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T12:00:00Z");
    WorkItemUnassigned event =
        new WorkItemUnassigned(priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 2L, occurredAt);
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(
            Optional.of(paData(priorAuthorityId, applicationId, PriorAuthorityType.EXPERT)));

    projection.on(event, message(event, "pa-unassign-event-id"));

    var captor = ArgumentCaptor.forClass(PriorAuthorityHistoryReadModel.class);
    verify(paRepository).save(captor.capture());
    var saved = captor.getValue();
    assertThat(saved.getEventId()).isEqualTo("pa-unassign-event-id");
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getPriorAuthorityType()).isEqualTo("EXPERT");
    assertThat(saved.getEventType()).isEqualTo("UNASSIGN_APPLICATION_TO_CASEWORKER");
    assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    var payload = objectMapper.readTree(saved.getEventData());
    assertThat(payload.get("workItemType").asString()).isEqualTo("PRIOR_AUTHORITY");
    assertThat(payload.get("caseworkerId")).isNull();
  }

  @Test
  void givenApplicationWorkItemUnassigned_whenHandled_thenIgnored() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T12:00:00Z");
    WorkItemUnassigned event =
        new WorkItemUnassigned(applicationId, WorkItemType.APPLICATION, 1L, 2L, occurredAt);

    projection.on(event, message(event, "app-unassign-event-id"));

    verify(paRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(paDataRepository, never())
        .findFirstByPriorAuthorityId(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenPriorAuthorityWorkItemUnassignedButNoDataFound_whenHandled_thenNothingSaved() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-05T12:00:00Z");
    WorkItemUnassigned event =
        new WorkItemUnassigned(priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 2L, occurredAt);
    when(paDataRepository.findFirstByPriorAuthorityId(priorAuthorityId))
        .thenReturn(Optional.empty());

    projection.on(event, message(event, "pa-unassign-event-id"));

    verify(paRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenReset_whenHandled_thenDeletesPaHistoryTable() {
    projection.reset();

    verify(paRepository).deleteAllInBatch();
  }

  private PriorAuthorityData paData(
      UUID priorAuthorityId, UUID applicationId, PriorAuthorityType type) {
    PriorAuthorityContent content = new PriorAuthorityContent(type, null, null, null, null);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, null, Instant.now());
    return PriorAuthorityData.builder()
        .applicationId(applicationId)
        .payload(payload)
        .payloadHash("hash")
        .createdAt(Instant.now())
        .build();
  }

  private EventMessage message(Object payload, String identifier) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(ServiceNameMetadataDispatchInterceptor.SERVICE_NAME_METADATA_KEY, "CIVIL_APPLY"),
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private EventMessage messageWithoutServiceName(Object payload, String identifier) {
    return new GenericEventMessage(
        identifier,
        new MessageType(payload.getClass()),
        payload,
        Map.of(),
        Instant.parse("2026-07-15T08:00:00Z"));
  }
}
