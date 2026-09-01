package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectPriorAuthorityWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemId;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

class DirectPriorAuthorityWorkItemAssignmentTest {
  private AxonTestFixture fixture;
  private PriorAuthorityDataStore dataStore;

  @BeforeEach
  void setUp() {
    dataStore = org.mockito.Mockito.mock(PriorAuthorityDataStore.class);
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(
                        UUID.class, PriorAuthorityAggregate.class))
                .componentRegistry(
                    registry ->
                        registry.registerComponent(PriorAuthorityDataStore.class, c -> dataStore)));
  }

  @Test
  void assignsAndUnassignsCreatedPriorAuthorityUsingTheSameWorkItemVocabulary() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    WorkItemId item = new WorkItemId(WorkItemType.PRIOR_AUTHORITY, submissionId);
    PriorAuthorityDataPayload payload = org.mockito.Mockito.mock(PriorAuthorityDataPayload.class);
    when(payload.withAssignment(any())).thenReturn(payload);
    when(dataStore.get(submissionId, 0L)).thenReturn(payload);
    when(dataStore.get(submissionId, 1L)).thenReturn(payload);
    when(dataStore.append(any(), anyLong(), any(), any(), any(), any())).thenReturn("hash");

    fixture
        .given()
        .events(created(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, item, caseworkerId, 0L, "{}", "Assigned", when))
        .then()
        .events(new WorkItemAssigned(item, null, submissionId, 1L, 1L, 1L, caseworkerId, when));

    fixture
        .given()
        .events(
            created(submissionId, applicationId, when),
            new WorkItemAssigned(item, null, submissionId, 1L, 1L, 1L, caseworkerId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(
                submissionId, item, 1L, "{}", "Unassigned", when))
        .then()
        .events(new WorkItemUnassigned(item, null, submissionId, 2L, 2L, 2L, when));
  }

  @Test
  void rejectsRepeatedAssignmentAtTheCurrentAssignmentVersion() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    WorkItemId item = new WorkItemId(WorkItemType.PRIOR_AUTHORITY, submissionId);
    fixture
        .given()
        .events(
            created(submissionId, applicationId, when),
            new WorkItemAssigned(item, null, submissionId, 1L, 1L, 1L, caseworkerId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, item, UUID.randomUUID(), 1L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  private PriorAuthorityCreatedEvent created(UUID submissionId, UUID applicationId, Instant when) {
    return new PriorAuthorityCreatedEvent(
        submissionId, applicationId, 0L, "hash", "PENDING", 1, when);
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
