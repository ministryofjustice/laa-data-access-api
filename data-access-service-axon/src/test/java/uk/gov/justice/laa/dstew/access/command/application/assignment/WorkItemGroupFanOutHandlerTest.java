package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.workitem.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemGroupFanOutHandler;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadModel;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

class WorkItemGroupFanOutHandlerTest {

  private CommandGateway commandGateway;
  private LinkedApplicationGroupReadRepository repository;
  private WorkItemGroupFanOutHandler handler;

  @BeforeEach
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    repository = mock(LinkedApplicationGroupReadRepository.class);
    handler = new WorkItemGroupFanOutHandler(commandGateway, repository);
  }

  @Test
  void givenLeadAssignment_whenHandled_thenFansOutToMembers() {
    UUID leadId = UUID.randomUUID();
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(repository.findByLeadApplicationId(leadId))
        .thenReturn(Optional.of(group(leadId, member1, member2)));

    handler.on(new WorkItemAssigned(WorkItemId.toAggregateId(leadId), caseworkerId, "{}", "Assigned", Instant.now(), false));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(captor.capture());
    assertThat(captor.getAllValues().stream().filter(AssignWorkItemCommand.class::isInstance).map(AssignWorkItemCommand.class::cast).map(AssignWorkItemCommand::workItemId).toList())
        .containsExactlyInAnyOrder(WorkItemId.toAggregateId(member1), WorkItemId.toAggregateId(member2));
  }

  @Test
  void givenMemberAssignment_whenHandled_thenAuditsAndFansOutToLeadAndPeers() {
    UUID leadId = UUID.randomUUID();
    UUID member1 = UUID.randomUUID();
    UUID member2 = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    when(repository.findByLeadApplicationId(member1)).thenReturn(Optional.empty());
    when(repository.findByMemberIdsContaining(member1))
        .thenReturn(Optional.of(group(leadId, member1, member2)));

    handler.on(new WorkItemAssigned(WorkItemId.toAggregateId(member1), caseworkerId, "{}", "Assigned", Instant.now(), false));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(commandGateway, org.mockito.Mockito.times(2)).sendAndWait(captor.capture());
    assertThat(captor.getAllValues().stream().filter(AssignWorkItemCommand.class::isInstance).map(AssignWorkItemCommand.class::cast).map(AssignWorkItemCommand::workItemId).toList())
        .containsExactlyInAnyOrder(WorkItemId.toAggregateId(leadId), WorkItemId.toAggregateId(member2));
  }

  @Test
  void givenUnlinkedAssignment_whenHandled_thenDoesNotDispatchFurtherCommands() {
    UUID workItemId = UUID.randomUUID();
    when(repository.findByLeadApplicationId(workItemId)).thenReturn(Optional.empty());
    when(repository.findByMemberIdsContaining(workItemId)).thenReturn(Optional.empty());

    handler.on(new WorkItemAssigned(WorkItemId.toAggregateId(workItemId), UUID.randomUUID(), "{}", "Assigned", Instant.now(), false));

    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenFanOutAssignment_whenHandled_thenDoesNotReFanOut() {
    UUID workItemId = UUID.randomUUID();

    handler.on(new WorkItemAssigned(WorkItemId.toAggregateId(workItemId), UUID.randomUUID(), "{}", "Assigned", Instant.now(), true));

    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
    verify(repository, never()).findByLeadApplicationId(any());
  }


  private LinkedApplicationGroupReadModel group(UUID leadId, UUID... members) {
    return LinkedApplicationGroupReadModel.builder()
        .groupId(UUID.randomUUID())
        .leadApplicationId(leadId)
        .memberIds(List.of(members))
        .build();
  }
}
