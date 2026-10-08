package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectGroupWorkItemAssignmentCommand;

/** Unit tests for the overwrite-style assignment used to propagate across a linked group. */
class DirectGroupWorkItemAssignmentTest {
  private AxonTestFixture fixture;
  private ApplicationDataStore dataStore;

  @BeforeEach
  void setUp() {
    dataStore = mock(ApplicationDataStore.class);
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
  void assignsAnUnassignedEligibleApplicationRegardlessOfVersion() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    fixture
        .given()
        .events(created(id, when), new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when))
        .when()
        .command(new DirectGroupWorkItemAssignmentCommand(id, caseworkerId, "{}", "Assigned", when))
        .then()
        .events(new WorkItemAssigned(id, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, when));
    verifyNoInteractions(dataStore);
  }

  @Test
  void overwritesAnAlreadyAssignedApplicationWithoutConflicting() {
    UUID id = UUID.randomUUID();
    UUID previousCaseworkerId = UUID.randomUUID();
    UUID newCaseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    ApplicationAggregate aggregate = new ApplicationAggregate();
    aggregate.on(created(id, when));
    aggregate.on(new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when));
    aggregate.on(
        new WorkItemAssigned(id, WorkItemType.APPLICATION, 1L, 1L, previousCaseworkerId, when));

    EventAppender appender = mock(EventAppender.class);
    aggregate.handle(
        new DirectGroupWorkItemAssignmentCommand(id, newCaseworkerId, "{}", "Reassigned", when),
        appender);

    ArgumentCaptor<WorkItemAssigned> eventCaptor = ArgumentCaptor.forClass(WorkItemAssigned.class);
    verify(appender).append(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isEqualTo(
            new WorkItemAssigned(id, WorkItemType.APPLICATION, 1L, 2L, newCaseworkerId, when));
  }

  @Test
  void noOpsWhenAlreadyAssignedToTheSameCaseworker() {
    UUID id = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");
    ApplicationAggregate aggregate = new ApplicationAggregate();
    aggregate.on(created(id, when));
    aggregate.on(new ApplicationReadyForManualAssessmentEvent(id, 1L, 1L, when));
    aggregate.on(new WorkItemAssigned(id, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, when));

    EventAppender appender = mock(EventAppender.class);
    aggregate.handle(
        new DirectGroupWorkItemAssignmentCommand(id, caseworkerId, "{}", "Assigned", when),
        appender);

    verifyNoInteractions(appender);
  }

  @Test
  void noOpsForAMemberThatIsNotAnActiveWorkItem() {
    UUID caseworkerId = UUID.randomUUID();
    Instant when = Instant.parse("2026-08-28T10:00:00Z");

    UUID decidedId = UUID.randomUUID();
    ApplicationAggregate decided = new ApplicationAggregate();
    decided.on(created(decidedId, when));
    decided.on(new ApplicationReadyForManualAssessmentEvent(decidedId, 1L, 1L, when));
    decided.on(
        new ApplicationDecisionMadeEvent(
            decidedId, 2L, 2L, "GRANTED", AutoGrantedState.MANUAL, when));
    EventAppender appender = mock(EventAppender.class);
    decided.handle(
        new DirectGroupWorkItemAssignmentCommand(decidedId, caseworkerId, "{}", "Assigned", when),
        appender);
    verifyNoInteractions(appender);

    UUID draftId = UUID.randomUUID();
    ApplicationAggregate draftOnly = new ApplicationAggregate();
    draftOnly.on(created(draftId, when));
    EventAppender draftAppender = mock(EventAppender.class);
    draftOnly.handle(
        new DirectGroupWorkItemAssignmentCommand(draftId, caseworkerId, "{}", "Assigned", when),
        draftAppender);
    verifyNoInteractions(draftAppender);
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  private ApplicationCreatedEvent created(UUID id, Instant occurredAt) {
    return new ApplicationCreatedEvent(
        id, 0L, "hash", "APPLICATION_SUBMITTED", 1, occurredAt, null);
  }
}
