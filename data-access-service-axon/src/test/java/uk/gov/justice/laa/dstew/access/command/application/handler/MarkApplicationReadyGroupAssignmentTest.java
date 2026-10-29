package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeAssigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeUnassigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.notReady;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.resubmittedAfterAssignment;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.stateOf;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupAggregate;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationLinkConflictException;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.MarkApplicationReadyCommand;
import uk.gov.justice.laa.dstew.access.command.application.ready.ReadyApplicationResult;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;

@ExtendWith(MockitoExtension.class)
class MarkApplicationReadyGroupAssignmentTest {
  private static final UUID GROUP_ID = UUID.randomUUID();
  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();
  private static final UUID CASEWORKER_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");

  @Mock private StateManager stateManager;
  @Mock private ProcessingContext context;
  @Mock private EventAppender eventAppender;
  @Mock private ApplicationDataStore applicationDataStore;

  private MarkApplicationReadyCommandHandler handler;
  private ApplicationAggregate application;

  @BeforeEach
  void setUp() {
    handler = new MarkApplicationReadyCommandHandler();
    application = mock(ApplicationAggregate.class);
  }

  @Test
  void givenGroupAssignedToC1_whenMemberReady_thenReadyEventThenAssignedToC1() {
    givenApplicationState(stateOf(notReady(APPLICATION_ID)));
    givenDataStore(0L);
    givenGroup(List.of(APPLICATION_ID, OTHER_MEMBER_ID));
    givenMember(OTHER_MEMBER_ID, activeAssigned(OTHER_MEMBER_ID, CASEWORKER_ID));

    var result =
        handler.handle(
            command(), application, applicationDataStore, eventAppender, stateManager, context);

    assertThat(result).isEqualTo(ReadyApplicationResult.RECORDED);
    verify(eventAppender)
        .append(
            List.of(
                new ApplicationReadyForManualAssessmentEvent(APPLICATION_ID, 1L, 1L, OCCURRED_AT),
                new WorkItemAssigned(
                    APPLICATION_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT)));
  }

  @Test
  void givenResubmittedMemberOfUnassignedGroup_whenReady_thenReadyEventThenUnassigned() {
    givenApplicationState(stateOf(resubmittedAfterAssignment(APPLICATION_ID, CASEWORKER_ID)));
    givenDataStore(2L);
    givenGroup(List.of(APPLICATION_ID, OTHER_MEMBER_ID));
    givenMember(OTHER_MEMBER_ID, activeUnassigned(OTHER_MEMBER_ID));

    handler.handle(
        new MarkApplicationReadyCommand(APPLICATION_ID, 2L, "{}", OCCURRED_AT)
            .withLinkedGroupId(GROUP_ID),
        application,
        applicationDataStore,
        eventAppender,
        stateManager,
        context);

    verify(eventAppender)
        .append(
            List.of(
                new ApplicationReadyForManualAssessmentEvent(APPLICATION_ID, 3L, 3L, OCCURRED_AT),
                new WorkItemUnassigned(
                    APPLICATION_ID, WorkItemType.APPLICATION, 3L, 2L, OCCURRED_AT)));
  }

  @Test
  void givenStandalone_whenReady_thenOnlyReadyEvent() {
    givenApplicationState(stateOf(notReady(APPLICATION_ID)));
    givenDataStore(0L);
    handler.handle(
        command().withLinkedGroupId(null),
        application,
        applicationDataStore,
        eventAppender,
        stateManager,
        context);

    verify(eventAppender)
        .append(
            List.of(
                new ApplicationReadyForManualAssessmentEvent(APPLICATION_ID, 1L, 1L, OCCURRED_AT)));
    verifyNoInteractions(stateManager);
  }

  @Test
  void givenGroupWithoutMembers_whenReady_thenRejectsChangedMembership() {
    givenApplicationState(stateOf(notReady(APPLICATION_ID)));
    givenDataStore(0L);
    givenGroup(List.of());

    assertThatThrownBy(
            () ->
                handler.handle(
                    command(),
                    application,
                    applicationDataStore,
                    eventAppender,
                    stateManager,
                    context))
        .isInstanceOf(ApplicationLinkConflictException.class)
        .hasMessageContaining("membership of application " + APPLICATION_ID + " changed");

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenAlreadyReady_whenReadyAgain_thenNoEvents() {
    givenApplicationState(
        stateOf(
            List.of(
                notReady(APPLICATION_ID).getFirst(),
                new ApplicationReadyForManualAssessmentEvent(
                    APPLICATION_ID, 1L, 1L, OCCURRED_AT))));

    var result =
        handler.handle(
            command(), application, applicationDataStore, eventAppender, stateManager, context);

    assertThat(result).isEqualTo(ReadyApplicationResult.ALREADY_RECORDED);
    verifyNoInteractions(eventAppender, stateManager, applicationDataStore);
  }

  private MarkApplicationReadyCommand command() {
    return new MarkApplicationReadyCommand(APPLICATION_ID, 0L, "{}", OCCURRED_AT)
        .withLinkedGroupId(GROUP_ID);
  }

  private void givenGroup(List<UUID> members) {
    var group = mock(LinkedApplicationGroupAggregate.class);
    var state = mock(LinkedApplicationGroupState.class);
    when(state.getGroupId()).thenReturn(GROUP_ID);
    when(state.memberIdsOrEmpty()).thenReturn(members);
    when(group.getState()).thenReturn(state);
    when(stateManager.loadEntity(LinkedApplicationGroupAggregate.class, GROUP_ID, context))
        .thenReturn(CompletableFuture.completedFuture(group));
  }

  private void givenApplicationState(ApplicationState state) {
    when(application.getState()).thenReturn(state);
  }

  private void givenDataStore(long dataVersion) {
    when(applicationDataStore.get(APPLICATION_ID, dataVersion))
        .thenReturn(ApplicationDataPayload.from(applicationCreationDetails(APPLICATION_ID)));
    when(applicationDataStore.append(any(), anyLong(), any(), any(), any())).thenReturn("hash");
  }

  private void givenMember(UUID applicationId, List<Object> events) {
    var aggregate = mock(ApplicationAggregate.class);
    ApplicationState state = stateOf(events);
    when(aggregate.getState()).thenReturn(state);
    when(stateManager.loadEntity(ApplicationAggregate.class, applicationId, context))
        .thenReturn(CompletableFuture.completedFuture(aggregate));
  }
}
