package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectPriorAuthorityWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

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
  void assignsCreatedPriorAuthorityUsingItsCanonicalWorkItemId() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");

    fixture
        .given()
        .events(created(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, caseworkerId, 0L, "{}", "Assigned", when))
        .then()
        .events(
            new WorkItemAssigned(
                submissionId,
                WorkItemType.PRIOR_AUTHORITY,
                0L,
                1L,
                caseworkerId,
                "Assigned",
                when));
    verifyNoInteractions(dataStore);
  }

  @Test
  void rejectsRepeatedAssignmentAtTheCurrentAssignmentVersion() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    fixture
        .given()
        .events(
            created(submissionId, applicationId, when),
            new WorkItemAssigned(
                submissionId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, "", when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, UUID.randomUUID(), 1L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @Test
  void unassignsAssignedPriorAuthorityAndRejectsAlreadyUnassignedWork() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    WorkItemAssigned assigned =
        new WorkItemAssigned(
            submissionId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, "Assigned", when);

    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(created(submissionId, applicationId, when));
    aggregate.on(assigned);
    EventAppender eventAppender = org.mockito.Mockito.mock(EventAppender.class);

    aggregate.handle(
        new DirectPriorAuthorityWorkItemUnassignmentCommand(submissionId, 1L, "{}", "", when),
        eventAppender);

    verify(eventAppender)
        .append(
            new WorkItemUnassigned(submissionId, WorkItemType.PRIOR_AUTHORITY, 0L, 2L, "", when));
    aggregate.on(
        new WorkItemUnassigned(submissionId, WorkItemType.PRIOR_AUTHORITY, 0L, 2L, "", when));

    fixture
        .given()
        .events(created(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(submissionId, 0L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @Test
  void rejectsARehydratedPriorAuthorityWithAMismatchedWorkItemId() {
    UUID submissionId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(created(submissionId, UUID.randomUUID(), when));

    assertThatThrownBy(
            () ->
                aggregate.handle(
                    new DirectPriorAuthorityWorkItemAssignmentCommand(
                        UUID.randomUUID(), UUID.randomUUID(), 0L, "{}", "", when),
                    org.mockito.Mockito.mock(EventAppender.class)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void rejectsUnknownMismatchedAndStaleDirectAssignments() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");

    fixture
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, UUID.randomUUID(), 0L, "{}", "", when))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
    fixture
        .given()
        .events(created(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                UUID.randomUUID(), UUID.randomUUID(), 0L, "{}", "", when))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
    fixture
        .given()
        .events(created(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, UUID.randomUUID(), 1L, "{}", "", when))
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
