package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeAssigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.activeUnassigned;
import static uk.gov.justice.laa.dstew.access.command.application.ApplicationStateEvents.stateOf;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.StateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.AddApplicationToLinkedGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.handler.EstablishLinkedApplicationGroupCommandHandler;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

@ExtendWith(MockitoExtension.class)
class LinkedApplicationGroupAssignmentHandlerWiringTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-27T12:00:00Z");
  private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID LEAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID JOINING_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID CASEWORKER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Mock private StateManager stateManager;
  @Mock private ProcessingContext context;
  @Mock private EventAppender eventAppender;
  @Mock private LinkedApplicationGroupAggregate group;
  @Mock private ApplicationAggregate leadAggregate;
  @Mock private ApplicationAggregate memberAggregate;
  @Mock private ApplicationAggregate joiningAggregate;

  @Captor private ArgumentCaptor<List<Object>> eventsCaptor;

  @Test
  void givenTargetAssignedSourceUnassigned_whenEstablish_thenGroupEventThenSourceAssigned() {
    var lead = stateOf(activeAssigned(LEAD_ID, CASEWORKER_ID));
    var member = stateOf(activeUnassigned(MEMBER_ID));
    givenApplication(LEAD_ID, leadAggregate, lead);
    givenApplication(MEMBER_ID, memberAggregate, member);
    var command =
        new EstablishLinkedApplicationGroupCommand(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    new EstablishLinkedApplicationGroupCommandHandler()
        .handle(command, null, stateManager, context, eventAppender);

    verify(eventAppender).append(eventsCaptor.capture());
    assertThat(eventsCaptor.getValue())
        .containsExactly(
            new LinkedApplicationGroupCreatedEvent(
                GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT),
            new WorkItemAssigned(
                MEMBER_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT));
  }

  @Test
  void givenTargetUnassignedSourceAssigned_whenEstablish_thenGroupEventThenTargetAssigned() {
    var lead = stateOf(activeUnassigned(LEAD_ID));
    var member = stateOf(activeAssigned(MEMBER_ID, CASEWORKER_ID));
    givenApplication(LEAD_ID, leadAggregate, lead);
    givenApplication(MEMBER_ID, memberAggregate, member);
    var command =
        new EstablishLinkedApplicationGroupCommand(
            GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT);

    new EstablishLinkedApplicationGroupCommandHandler()
        .handle(command, null, stateManager, context, eventAppender);

    verify(eventAppender).append(eventsCaptor.capture());
    assertThat(eventsCaptor.getValue())
        .containsExactly(
            new LinkedApplicationGroupCreatedEvent(
                GROUP_ID, LEAD_ID, List.of(LEAD_ID, MEMBER_ID), OCCURRED_AT),
            new WorkItemAssigned(
                LEAD_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT));
  }

  @Test
  void givenAssignedGroup_whenUnassignedMemberAdded_thenMemberAddedThenMemberAssigned() {
    var groupState = LinkedApplicationGroupStates.group(GROUP_ID, List.of(LEAD_ID, MEMBER_ID));
    when(group.getState()).thenReturn(groupState);
    givenApplication(LEAD_ID, leadAggregate, stateOf(activeAssigned(LEAD_ID, CASEWORKER_ID)));
    givenApplication(MEMBER_ID, memberAggregate, stateOf(activeAssigned(MEMBER_ID, CASEWORKER_ID)));
    givenApplication(JOINING_ID, joiningAggregate, stateOf(activeUnassigned(JOINING_ID)));

    new AddApplicationToLinkedGroupCommandHandler()
        .handle(
            new AddApplicationToLinkedGroupCommand(GROUP_ID, JOINING_ID, 0L, OCCURRED_AT),
            group,
            stateManager,
            context,
            eventAppender);

    verify(eventAppender).append(eventsCaptor.capture());
    assertThat(eventsCaptor.getValue())
        .containsExactly(
            new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, JOINING_ID, OCCURRED_AT),
            new WorkItemAssigned(
                JOINING_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT));
  }

  @Test
  void givenUnassignedGroup_whenAssignedMemberAdded_thenEveryActiveMemberAssigned() {
    var groupState = LinkedApplicationGroupStates.group(GROUP_ID, List.of(LEAD_ID, MEMBER_ID));
    when(group.getState()).thenReturn(groupState);
    givenApplication(LEAD_ID, leadAggregate, stateOf(activeUnassigned(LEAD_ID)));
    givenApplication(MEMBER_ID, memberAggregate, stateOf(activeUnassigned(MEMBER_ID)));
    givenApplication(
        JOINING_ID, joiningAggregate, stateOf(activeAssigned(JOINING_ID, CASEWORKER_ID)));

    new AddApplicationToLinkedGroupCommandHandler()
        .handle(
            new AddApplicationToLinkedGroupCommand(GROUP_ID, JOINING_ID, 0L, OCCURRED_AT),
            group,
            stateManager,
            context,
            eventAppender);

    verify(eventAppender).append(eventsCaptor.capture());
    assertThat(eventsCaptor.getValue())
        .containsExactly(
            new MemberAddedToGroupEvent(GROUP_ID, LEAD_ID, JOINING_ID, OCCURRED_AT),
            new WorkItemAssigned(
                LEAD_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT),
            new WorkItemAssigned(
                MEMBER_ID, WorkItemType.APPLICATION, 1L, 1L, CASEWORKER_ID, OCCURRED_AT));
  }

  private void givenApplication(
      UUID applicationId, ApplicationAggregate aggregate, ApplicationState state) {
    when(aggregate.getState()).thenReturn(state);
    when(stateManager.loadEntity(eq(ApplicationAggregate.class), eq(applicationId), eq(context)))
        .thenReturn(CompletableFuture.completedFuture(aggregate));
  }
}
