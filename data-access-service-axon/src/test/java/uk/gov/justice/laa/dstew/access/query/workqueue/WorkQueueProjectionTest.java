package uk.gov.justice.laa.dstew.access.query.workqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemUnassigned;

class WorkQueueProjectionTest {

  private WorkQueueReadRepository repository;
  private ApplicationDataStore applicationDataStore;
  private WorkQueueProjection projection;

  @BeforeEach
  void setUp() {
    repository = mock(WorkQueueReadRepository.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    when(applicationDataStore.get(any(), anyLong()))
        .thenAnswer(
            invocation ->
                ApplicationDataPayload.from(applicationCreationDetails(invocation.getArgument(0))));
    projection = new WorkQueueProjection(repository, applicationDataStore);
  }

  @Test
  void givenCreatedEvent_whenHandled_thenInsertsWorkQueueRow() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);

    projection.on(event);

    verify(repository).save(any(WorkQueueReadModel.class));
  }

  @Test
  void givenCreatedEvent_whenHandled_thenRowHasCorrectFields() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent event = applicationCreatedEvent(applicationId);
    WorkQueueReadModel[] saved = new WorkQueueReadModel[1];
    when(repository.save(any())).thenAnswer(inv -> {
      saved[0] = inv.getArgument(0);
      return saved[0];
    });

    projection.on(event);

    assertThat(saved[0].getItemId()).isEqualTo(applicationId);
    assertThat(saved[0].getItemType()).isEqualTo(WorkQueueItemType.APPLICATION);
    assertThat(saved[0].getAssignedTo()).isNull();
    assertThat(saved[0].getLaaReference()).isEqualTo("LAA-123");
    assertThat(saved[0].getSubmittedAt()).isEqualTo(Instant.parse("2026-07-14T12:30:00Z"));
  }

  @Test
  void givenAssignedEvent_whenHandled_thenUpdatesAssignedTo() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    WorkQueueReadModel existing = WorkQueueReadModel.builder().itemId(applicationId).build();
    when(repository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new WorkItemAssigned(
            WorkItemId.toAggregateId(applicationId), caseworkerId, "{}", "Assigned", Instant.now(), false));

    assertThat(existing.getAssignedTo()).isEqualTo(caseworkerId);
    verify(repository).save(existing);
  }

  @Test
  void givenUnassignedEvent_whenHandled_thenClearsAssignedTo() {
    UUID applicationId = UUID.randomUUID();
    WorkQueueReadModel existing = WorkQueueReadModel.builder()
        .itemId(applicationId)
        .assignedTo(UUID.randomUUID())
        .build();
    when(repository.findById(applicationId)).thenReturn(Optional.of(existing));

    projection.on(
        new WorkItemUnassigned(
            WorkItemId.toAggregateId(applicationId), "{}", "Unassigned", Instant.now()));

    assertThat(existing.getAssignedTo()).isNull();
    verify(repository).save(existing);
  }

  @Test
  void givenDecisionEvent_whenHandled_thenDeletesRow() {
    UUID applicationId = UUID.randomUUID();
    QueryUpdateEmitter queryUpdateEmitter = mock(QueryUpdateEmitter.class);

    projection.on(
        new ApplicationDecisionMadeEvent(applicationId, 1L, 1L, "GRANTED", false, Instant.now()),
        queryUpdateEmitter);

    verify(repository).deleteById(applicationId);
    verify(queryUpdateEmitter).emit(eq(AwaitWorkQueueRemovalQuery.class), any(), eq(true));
  }

  @Test
  void givenRemovedWorkQueueItem_whenAwaitingRemoval_thenReportsQueueRowIsAbsent() {
    UUID applicationId = UUID.randomUUID();
    when(repository.existsById(applicationId)).thenReturn(false);

    boolean removed = projection.handle(new AwaitWorkQueueRemovalQuery(applicationId));

    assertThat(removed).isTrue();
  }

  @Test
  void givenResetCalled_whenHandled_thenDeletesAllRows() {
    projection.reset();

    verify(repository).deleteAllInBatch();
  }

  @Test
  void givenAssignedEventForUnknownItem_whenHandled_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(
        new WorkItemAssigned(
            WorkItemId.toAggregateId(applicationId),
            UUID.randomUUID(),
            "{}",
            "Assigned",
            Instant.now(),
            false));

    verify(repository, never()).save(any());
  }

  @Test
  void givenUnassignedEventForUnknownItem_whenHandled_thenDoesNothing() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(
        new WorkItemUnassigned(
            WorkItemId.toAggregateId(applicationId), "{}", "Unassigned", Instant.now()));

    verify(repository, never()).save(any());
  }
}
