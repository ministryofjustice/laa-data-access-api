package uk.gov.justice.laa.dstew.access.query.worklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.assignment.ApplicationAssignedToCaseworkerEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class WorkListProjectionTest {
  private WorkListItemReadRepository items;
  private WorkListProjection projection;

  @BeforeEach
  void setUp() {
    items = mock(WorkListItemReadRepository.class);
    projection = new WorkListProjection(items);
  }

  @Test
  void givenManualApplication_whenHandled_thenCreatesUnassignedApplicationWorkItem() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-28T10:00:00Z");

    projection.on(new ApplicationReadyForManualAssessmentEvent(applicationId, 3L, 5L, occurredAt), message());

    ArgumentCaptor<WorkListItemReadModel> captor = ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    WorkListItemReadModel row = captor.getValue();
    assertThat(row.getApplicationId()).isEqualTo(applicationId);
    assertThat(row.getParentApplicationId()).isNull();
    assertThat(row.getAssigneeId()).isNull();
    assertThat(row.getAssignmentBoundaryType()).isEqualTo("DIRECT");
    assertThat(row.getItemVersion()).isEqualTo(3L);
  }

  @Test
  void givenCreatedPriorAuthority_whenHandled_thenCreatesDirectWorkUnderItsParent() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    projection.on(
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            0L,
            "fingerprint",
            "PENDING",
            1,
            Instant.parse("2026-08-28T10:00:00Z")),
        message());

    ArgumentCaptor<WorkListItemReadModel> captor = ArgumentCaptor.forClass(WorkListItemReadModel.class);
    verify(items).save(captor.capture());
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getParentApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getAssignmentBoundaryId()).isEqualTo(submissionId);
  }

  @Test
  void givenActiveApplication_whenAssigned_thenUpdatesOnlyItsExistingWorkRow() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkListItemReadModel row =
        new WorkListItemReadModel(
            WorkItemType.APPLICATION, applicationId, applicationId, null, Instant.now(), 1L, 0L);
    when(items.findById(new WorkListItemId(WorkItemType.APPLICATION, applicationId)))
        .thenReturn(Optional.of(row));

    projection.on(
        new ApplicationAssignedToCaseworkerEvent(applicationId, 2L, 3L, caseworkerId, Instant.now()),
        message());

    assertThat(row.getAssigneeId()).isEqualTo(caseworkerId);
    assertThat(row.getItemVersion()).isEqualTo(2L);
    verify(items).save(row);
  }

  @Test
  void givenFinalApplicationDecision_whenHandled_thenDeletesOnlyItsApplicationWorkItem() {
    UUID applicationId = UUID.randomUUID();

    projection.on(new ApplicationDecisionMadeEvent(applicationId, 2L, 3L, "REFUSED", null, Instant.now()));

    verify(items).deleteByIdItemTypeAndIdItemId(WorkItemType.APPLICATION, applicationId);
  }

  @Test
  void givenReset_whenHandled_thenDeletesTheDisposableProjection() {
    projection.reset();

    verify(items).deleteAllInBatch();
  }

  private static EventMessage message() {
    return new GenericEventMessage(
        "test-id", new MessageType(String.class), "test", Map.of(), Instant.now());
  }
}

