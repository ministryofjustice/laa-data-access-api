package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.handler.DelegateAssignmentToGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.DirectApplicationWorkItemAssignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.DirectApplicationWorkItemUnassignmentCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.ReclaimAssignmentFromGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentDelegatedToGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ApplicationAssignmentReclaimedFromGroupEvent;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegateAssignmentToGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.DelegatedAssignment;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ReclaimAssignmentFromGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectWorkItemUnassignmentCommand;

class ApplicationGroupAssignmentHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-01T10:00:00Z");
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private AxonTestFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
                .registerCommandHandlingModule(
                    CommandHandlingModule.named("application-group-assignment-command-handlers")
                        .commandHandlers(
                            handlers ->
                                handlers
                                    .autodetectedCommandHandlingComponent(
                                        configuration ->
                                            new DelegateAssignmentToGroupCommandHandler())
                                    .autodetectedCommandHandlingComponent(
                                        configuration ->
                                            new ReclaimAssignmentFromGroupCommandHandler())
                                    .autodetectedCommandHandlingComponent(
                                        configuration ->
                                            new DirectApplicationWorkItemAssignmentCommandHandler())
                                    .autodetectedCommandHandlingComponent(
                                        configuration ->
                                            new DirectApplicationWorkItemUnassignmentCommandHandler())))
                .componentRegistry(
                    registry ->
                        registry.registerComponent(
                            ApplicationDataStore.class,
                            configuration -> mock(ApplicationDataStore.class))));
  }

  @Test
  void givenAssignedActiveApplication_whenDelegated_thenReturnsCaseworker() {
    ApplicationAggregate application = new ApplicationAggregate();
    application.on(created());
    application.on(ready());
    application.on(assigned(CASEWORKER_ID, 1L));
    EventAppender eventAppender = mock(EventAppender.class);
    doAnswer(
            invocation -> {
              ApplicationAssignmentDelegatedToGroupEvent event = invocation.getArgument(0);
              application.on(event);
              return null;
            })
        .when(eventAppender)
        .append(any(ApplicationAssignmentDelegatedToGroupEvent.class));

    var result =
        new DelegateAssignmentToGroupCommandHandler()
            .handle(
                new DelegateAssignmentToGroupCommand(APPLICATION_ID, GROUP_ID, OCCURRED_AT),
                application,
                eventAppender);

    assertThat(result).isEqualTo(new DelegatedAssignment(CASEWORKER_ID));
    assertThat(application.getState().getCaseworkerId()).isNull();
  }

  @Test
  void givenDelegatedApplication_whenDelegatedToSameGroup_thenNoEvents() {
    fixture
        .given()
        .events(
            created(),
            ready(),
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .when()
        .command(new DelegateAssignmentToGroupCommand(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .then()
        .resultMessagePayload(new DelegatedAssignment(null))
        .noEvents();
  }

  @Test
  void givenDelegatedApplication_whenDirectlyAssigned_thenRejected() {
    fixture
        .given()
        .events(
            created(),
            ready(),
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .when()
        .command(
            new DirectWorkItemAssignmentCommand(
                APPLICATION_ID, CASEWORKER_ID, 0L, "{}", "Assigned", OCCURRED_AT))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents()
        .exceptionSatisfies(
            exception ->
                assertThat(exception)
                    .hasMessageContaining("its assignment is owned by a linked group"));
  }

  @Test
  void givenDelegatedApplication_whenDirectlyUnassigned_thenRejected() {
    fixture
        .given()
        .events(
            created(),
            ready(),
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .when()
        .command(new DirectWorkItemUnassignmentCommand(APPLICATION_ID, 0L, "{}", "", OCCURRED_AT))
        .then()
        .exception(WorkItemAssignmentConflictException.class)
        .noEvents()
        .exceptionSatisfies(
            exception ->
                assertThat(exception)
                    .hasMessageContaining("its assignment is owned by a linked group"));
  }

  @Test
  void givenDelegatedActiveApplication_whenReclaimed_thenTakesGroupAssignment() {
    fixture
        .given()
        .events(
            created(),
            ready(),
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .when()
        .command(
            new ReclaimAssignmentFromGroupCommand(
                APPLICATION_ID, GROUP_ID, CASEWORKER_ID, 4L, OCCURRED_AT))
        .then()
        .events(
            new ApplicationAssignmentReclaimedFromGroupEvent(
                APPLICATION_ID, GROUP_ID, CASEWORKER_ID, 5L, OCCURRED_AT));
  }

  @Test
  void givenReclaimedApplication_whenDirectlyUnassigned_thenUsesReclaimedVersion() {
    fixture
        .given()
        .events(
            created(),
            ready(),
            new ApplicationAssignmentDelegatedToGroupEvent(APPLICATION_ID, GROUP_ID, OCCURRED_AT),
            new ApplicationAssignmentReclaimedFromGroupEvent(
                APPLICATION_ID, GROUP_ID, CASEWORKER_ID, 5L, OCCURRED_AT))
        .when()
        .command(
            new DirectWorkItemUnassignmentCommand(
                APPLICATION_ID, 5L, "{}", "Unassigned", OCCURRED_AT))
        .then()
        .events(
            new WorkItemUnassigned(APPLICATION_ID, WorkItemType.APPLICATION, 1L, 6L, OCCURRED_AT));
  }

  @Test
  void givenMissingApplication_whenDelegated_thenNotFound() {
    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(new DelegateAssignmentToGroupCommand(APPLICATION_ID, GROUP_ID, OCCURRED_AT))
        .then()
        .exception(EntityNotFoundException.class)
        .noEvents();
  }

  private ApplicationCreatedEvent created() {
    return new ApplicationCreatedEvent(
        APPLICATION_ID, 0L, "hash", "APPLICATION_SUBMITTED", 1, OCCURRED_AT, null, null);
  }

  private ApplicationReadyForManualAssessmentEvent ready() {
    return new ApplicationReadyForManualAssessmentEvent(APPLICATION_ID, 1L, 1L, 0L, OCCURRED_AT);
  }

  private WorkItemAssigned assigned(UUID caseworkerId, long assignmentVersion) {
    return new WorkItemAssigned(
        APPLICATION_ID, WorkItemType.APPLICATION, 1L, assignmentVersion, caseworkerId, OCCURRED_AT);
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
