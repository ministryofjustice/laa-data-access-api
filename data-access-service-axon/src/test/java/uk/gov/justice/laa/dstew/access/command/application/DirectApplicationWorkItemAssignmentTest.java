package uk.gov.justice.laa.dstew.access.command.application;

import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.DirectWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class DirectApplicationWorkItemAssignmentTest {
  private AxonTestFixture fixture;
  private ApplicationDataStore dataStore;

  @BeforeEach
  void setUp() {
    dataStore = org.mockito.Mockito.mock(ApplicationDataStore.class);
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
                .componentRegistry(
                    registry ->
                        registry.registerComponent(ApplicationDataStore.class, c -> dataStore)));
  }

  @Test
  void assignsAnEligibleUnassignedApplicationAtTheExpectedAssignmentVersion() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    fixture
        .given()
        .events(created(id, when), new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when))
        .when()
        .command(new DirectWorkItemAssignmentCommand(id, caseworkerId, 0L, "{}", "Assigned", when))
        .then()
        .events(
            new WorkItemAssigned(
                id, WorkItemType.APPLICATION, 1L, 1L, 1L, caseworkerId, "Assigned", when));
    verifyNoInteractions(dataStore);
  }

  @Test
  void rejectsAStaleAssignmentAndAnAlreadyOpenUnassignment() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    fixture
        .given()
        .events(created(id, when), new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when))
        .when()
        .command(new DirectWorkItemAssignmentCommand(id, caseworkerId, 1L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();

    fixture
        .given()
        .events(created(id, when), new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when))
        .when()
        .command(new DirectWorkItemUnassignmentCommand(id, 0L, "{}", "", when))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents();
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  private ApplicationCreatedEvent created(UUID id, Instant occurredAt) {
    return new ApplicationCreatedEvent(
        id, 0L, "hash", "APPLICATION_SUBMITTED", 1, occurredAt, null, java.util.List.of());
  }
}
