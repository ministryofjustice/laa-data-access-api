package uk.gov.justice.laa.dstew.access.query.workitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.CaseworkerAssignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.CaseworkerUnassignedEvent;
import uk.gov.justice.laa.dstew.access.command.application.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.testutils.ApplicationSubmittedEventFixture;

class WorkItemProjectionTest {

  private final WorkItemRepository repository = mock(WorkItemRepository.class);
  private final ApplicationReadRepository applicationReadRepository =
      mock(ApplicationReadRepository.class);
  private final WorkItemProjection projection =
      new WorkItemProjection(repository, applicationReadRepository);

  @Test
  void givenApplicationSubmitted_whenProjected_thenAddsApplicationWorkItem() {
    UUID applicationId = UUID.randomUUID();
    var event = ApplicationSubmittedEventFixture.applicationSubmittedEvent(applicationId);
    when(repository.findById(applicationId)).thenReturn(Optional.empty());

    projection.on(event);

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getWorkItemId()).isEqualTo(applicationId);
    assertThat(saved.getWorkType()).isEqualTo(WorkType.APPLICATION);
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPriorAuthorityId()).isNull();
    assertThat(saved.getLaaReference()).isEqualTo("LAA-123");
    assertThat(saved.getAssignedCaseworkerId()).isNull();
    assertThat(saved.getCreatedAt()).isEqualTo(event.occurredAt());
    assertThat(saved.getUpdatedAt()).isEqualTo(event.occurredAt());
  }

  @Test
  void givenPriorAuthoritySubmitted_whenProjected_thenAddsPriorAuthorityItemWithParentReference() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-18T09:00:00Z");
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.empty());
    when(applicationReadRepository.findById(applicationId))
        .thenReturn(Optional.of(ApplicationReadModel.builder().laaReference("LAA-PARENT").build()));

    projection.on(new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityId, occurredAt));

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getWorkItemId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getWorkType()).isEqualTo(WorkType.PRIOR_AUTHORITY);
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getLaaReference()).isEqualTo("LAA-PARENT");
    assertThat(saved.getCreatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenExistingItem_whenResubmitted_thenPreservesCreatedAtAndRefreshesUpdatedAt() {
    UUID applicationId = UUID.randomUUID();
    var event = ApplicationSubmittedEventFixture.applicationSubmittedEvent(applicationId);
    when(repository.findById(applicationId))
        .thenReturn(
            Optional.of(
                WorkItemRecord.builder()
                    .workItemId(applicationId)
                    .createdAt(Instant.parse("2020-01-01T00:00:00Z"))
                    .build()));

    projection.on(event);

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
    assertThat(saved.getUpdatedAt()).isEqualTo(event.occurredAt());
  }

  @Test
  void givenExistingItem_whenCaseworkerAssigned_thenSetsAssigneeAndUpdatedAt() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:00:00Z");
    when(repository.findById(applicationId))
        .thenReturn(
            Optional.of(
                WorkItemRecord.builder()
                    .workItemId(applicationId)
                    .createdAt(Instant.parse("2026-07-18T09:00:00Z"))
                    .build()));

    projection.on(new CaseworkerAssignedEvent(applicationId, null, caseworkerId, occurredAt));

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getAssignedCaseworkerId()).isEqualTo(caseworkerId);
    assertThat(saved.getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenPriorAuthorityItem_whenCaseworkerAssigned_thenResolvesByPriorAuthorityId() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T10:00:00Z");
    when(repository.findById(priorAuthorityId))
        .thenReturn(Optional.of(WorkItemRecord.builder().workItemId(priorAuthorityId).build()));

    projection.on(
        new CaseworkerAssignedEvent(applicationId, priorAuthorityId, caseworkerId, occurredAt));

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getWorkItemId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getAssignedCaseworkerId()).isEqualTo(caseworkerId);
  }

  @Test
  void givenAssignedItem_whenCaseworkerUnassigned_thenClearsAssignee() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-19T11:00:00Z");
    when(repository.findById(applicationId))
        .thenReturn(
            Optional.of(
                WorkItemRecord.builder()
                    .workItemId(applicationId)
                    .assignedCaseworkerId(UUID.randomUUID())
                    .build()));

    projection.on(new CaseworkerUnassignedEvent(applicationId, null, occurredAt));

    WorkItemRecord saved = capturedSave();
    assertThat(saved.getAssignedCaseworkerId()).isNull();
    assertThat(saved.getUpdatedAt()).isEqualTo(occurredAt);
  }

  private WorkItemRecord capturedSave() {
    ArgumentCaptor<WorkItemRecord> captor = forClass(WorkItemRecord.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }
}
