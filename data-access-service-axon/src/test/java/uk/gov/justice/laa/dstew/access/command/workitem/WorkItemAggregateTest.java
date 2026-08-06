package uk.gov.justice.laa.dstew.access.command.workitem;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.exception.NotAssignedCaseworkerException;

class WorkItemAggregateTest {

  private AxonTestFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(UUID.class, WorkItemAggregate.class)));
  }

  @Test
  void givenWorkItem_whenAssigned_thenEmitsAssignedEvent() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-04T10:00:00Z");

    fixture
        .given()
        .when()
        .command(new AssignWorkItemCommand(workItemId, caseworkerId, "{}", "Assigned", occurredAt, false))
        .then()
        .events(new WorkItemAssigned(workItemId, caseworkerId, "{}", "Assigned", occurredAt, false));
  }

  @Test
  void givenAssignedWorkItem_whenAssignmentIsValidated_thenSucceedsWithoutEvents() {
    UUID applicationId = UUID.randomUUID();
    UUID workItemId = WorkItemId.toAggregateId(applicationId);
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given()
        .events(new WorkItemAssigned(workItemId, caseworkerId, "{}", "Assigned", Instant.now(), false))
        .when()
        .command(new ValidateWorkItemAssignmentCommand(workItemId, applicationId, caseworkerId))
        .then()
        .success()
        .noEvents();
  }

  @Test
  void givenMissingWorkItem_whenAssignmentIsValidated_thenThrowsNotAssigned() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(
            new ValidateWorkItemAssignmentCommand(
                WorkItemId.toAggregateId(applicationId), applicationId, UUID.randomUUID()))
        .then()
        .exception(NotAssignedCaseworkerException.class)
        .noEvents();
  }

  @Test
  void givenWorkItemAssignedToAnotherCaseworker_whenAssignmentIsValidated_thenThrowsNotAssigned() {
    UUID applicationId = UUID.randomUUID();
    UUID workItemId = WorkItemId.toAggregateId(applicationId);

    fixture
        .given()
        .events(new WorkItemAssigned(workItemId, UUID.randomUUID(), "{}", "Assigned", Instant.now(), false))
        .when()
        .command(new ValidateWorkItemAssignmentCommand(workItemId, applicationId, UUID.randomUUID()))
        .then()
        .exception(NotAssignedCaseworkerException.class)
        .noEvents();
  }

  @Test
  void givenUnassignedWorkItem_whenAssignmentIsValidated_thenThrowsNotAssigned() {
    UUID applicationId = UUID.randomUUID();
    UUID workItemId = WorkItemId.toAggregateId(applicationId);
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given()
        .events(
            new WorkItemAssigned(workItemId, caseworkerId, "{}", "Assigned", Instant.now(), false),
            new WorkItemUnassigned(workItemId, "{}", "Unassigned", Instant.now()))
        .when()
        .command(new ValidateWorkItemAssignmentCommand(workItemId, applicationId, caseworkerId))
        .then()
        .exception(NotAssignedCaseworkerException.class)
        .noEvents();
  }

  @Test
  void givenAssignedWorkItem_whenUnassigned_thenEmitsUnassignedEvent() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant assignedAt = Instant.parse("2026-08-04T10:00:00Z");
    Instant unassignedAt = Instant.parse("2026-08-04T10:05:00Z");

    fixture
        .given()
        .events(new WorkItemAssigned(workItemId, caseworkerId, "{}", "Assigned", assignedAt, false))
        .when()
        .command(new UnassignWorkItemCommand(workItemId, "{}", "Unassigned", unassignedAt))
        .then()
        .events(new WorkItemUnassigned(workItemId, "{}", "Unassigned", unassignedAt));
  }

  @Test
  void givenUnassignedWorkItem_whenUnassigned_thenDoesNothing() {
    fixture
        .given()
        .when()
        .command(new UnassignWorkItemCommand(UUID.randomUUID(), "{}", "Unassigned", Instant.now()))
        .then()
        .noEvents();
  }
}
