package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
    dataStore = mock(PriorAuthorityDataStore.class);
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
  void assignsSubmittedPriorAuthorityUsingItsCanonicalWorkItemId() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");

    fixture
        .given()
        .events(submitted(priorAuthorityId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                priorAuthorityId, caseworkerId, 0L, "{}", "Assigned", when))
        .then()
        .events(
            new WorkItemAssigned(
                priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 0L, 1L, caseworkerId, when));
    verifyNoInteractions(dataStore);
  }

  @Test
  void rejectsRepeatedAssignmentAtTheCurrentAssignmentVersion() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    fixture
        .given()
        .events(
            submitted(priorAuthorityId, applicationId, when),
            new WorkItemAssigned(
                priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                priorAuthorityId, UUID.randomUUID(), 1L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @Test
  void unassignsAssignedPriorAuthorityAndRejectsAlreadyUnassignedWork() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    WorkItemAssigned assigned =
        new WorkItemAssigned(
            priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 1L, 1L, caseworkerId, when);

    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(submitted(priorAuthorityId, applicationId, when));
    aggregate.on(assigned);
    EventAppender eventAppender = mock(EventAppender.class);

    aggregate.handle(
        new DirectPriorAuthorityWorkItemUnassignmentCommand(priorAuthorityId, 1L, "{}", "", when),
        eventAppender);

    verify(eventAppender)
        .append(
            new WorkItemUnassigned(priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 0L, 2L, when));
    aggregate.on(
        new WorkItemUnassigned(priorAuthorityId, WorkItemType.PRIOR_AUTHORITY, 0L, 2L, when));

    fixture
        .given()
        .events(submitted(priorAuthorityId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemUnassignmentCommand(
                priorAuthorityId, 0L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @Test
  void rejectsARehydratedPriorAuthorityWithAMismatchedWorkItemId() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(submitted(priorAuthorityId, UUID.randomUUID(), when));

    assertThatThrownBy(
            () ->
                aggregate.handle(
                    new DirectPriorAuthorityWorkItemAssignmentCommand(
                        UUID.randomUUID(), UUID.randomUUID(), 0L, "{}", "", when),
                    mock(EventAppender.class)))
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
        .events(submitted(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                UUID.randomUUID(), UUID.randomUUID(), 0L, "{}", "", when))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
    fixture
        .given()
        .events(submitted(submissionId, applicationId, when))
        .when()
        .command(
            new DirectPriorAuthorityWorkItemAssignmentCommand(
                submissionId, UUID.randomUUID(), 1L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  private PriorAuthoritySubmittedEvent submitted(
      UUID submissionId, UUID applicationId, Instant when) {
    return new PriorAuthoritySubmittedEvent(submissionId, applicationId, "type", 1, 0L, when);
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
