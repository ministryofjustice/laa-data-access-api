package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class DirectPriorAuthorityWorkItemAssignmentCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Test
  void givenValidPriorAuthority_whenHandle_thenAssignsWorkItem() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    DirectPriorAuthorityWorkItemAssignmentCommand command =
        new DirectPriorAuthorityWorkItemAssignmentCommand(
            workItemId, caseworkerId, 0L, "{}", "Assigned", OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(workItemId);
    when(priorAuthority.getAssignmentVersion()).thenReturn(0L);
    when(priorAuthority.getCaseworkerId()).thenReturn(null);
    when(priorAuthority.getDataVersion()).thenReturn(2L);

    new DirectPriorAuthorityWorkItemAssignmentCommandHandler()
        .handle(command, priorAuthority, eventAppender);

    verify(eventAppender)
        .append(
            new WorkItemAssigned(
                workItemId, WorkItemType.PRIOR_AUTHORITY, 2L, 1L, caseworkerId, OCCURRED_AT));
  }

  @Test
  void givenStaleVersionOrMissingPriorAuthority_whenHandle_thenThrows() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    DirectPriorAuthorityWorkItemAssignmentCommand command =
        new DirectPriorAuthorityWorkItemAssignmentCommand(
            workItemId, caseworkerId, 1L, "{}", "Assigned", OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(workItemId);
    when(priorAuthority.getAssignmentVersion()).thenReturn(0L);

    assertThatThrownBy(
            () ->
                new DirectPriorAuthorityWorkItemAssignmentCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage(
            "Work item " + workItemId + " cannot be updated: the assignment version is stale");

    when(priorAuthority.getPriorAuthorityId()).thenReturn(null);
    assertThatThrownBy(
            () ->
                new DirectPriorAuthorityWorkItemAssignmentCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No prior-authority work item found with id: " + workItemId);

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenAlreadyAssignedPriorAuthority_whenHandle_thenThrowsConflict() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    DirectPriorAuthorityWorkItemAssignmentCommand command =
        new DirectPriorAuthorityWorkItemAssignmentCommand(
            workItemId, caseworkerId, 0L, "{}", "Assigned", OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(workItemId);
    when(priorAuthority.getAssignmentVersion()).thenReturn(0L);
    when(priorAuthority.getCaseworkerId()).thenReturn(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                new DirectPriorAuthorityWorkItemAssignmentCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(WorkItemAssignmentConflictException.class)
        .hasMessage("Work item " + workItemId + " cannot be updated: it is already assigned");

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenDifferentPriorAuthorityId_whenHandle_thenThrowsResourceNotFound() {
    UUID workItemId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    DirectPriorAuthorityWorkItemAssignmentCommand command =
        new DirectPriorAuthorityWorkItemAssignmentCommand(
            workItemId, caseworkerId, 0L, "{}", "Assigned", OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                new DirectPriorAuthorityWorkItemAssignmentCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No prior-authority work item found with id: " + workItemId);

    verifyNoInteractions(eventAppender);
  }
}
