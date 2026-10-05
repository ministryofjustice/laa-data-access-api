package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemUnassigned;
import uk.gov.justice.laa.dstew.access.command.worklist.unassign.DirectPriorAuthorityWorkItemUnassignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles unassignment of prior-authority work items from caseworkers. */
@Component
@ExcludeFromGeneratedCodeCoverage
public class DirectPriorAuthorityWorkItemUnassignmentCommandHandler {

  /** Unassigns a prior-authority work item from a caseworker and emits the unassignment event. */
  @CommandHandler
  public void handle(
      DirectPriorAuthorityWorkItemUnassignmentCommand command,
      @InjectEntity(idProperty = "workItemId") PriorAuthorityAggregate priorAuthority,
      EventAppender eventAppender) {

    validateWorkItem(priorAuthority, command.workItemId(), command.expectedAssignmentVersion());

    if (priorAuthority.getCaseworkerId() == null) {
      throw new WorkItemAssignmentConflictException(
          command.workItemId(), "it is already unassigned");
    }

    eventAppender.append(
        new WorkItemUnassigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            priorAuthority.getDataVersion(),
            priorAuthority.getAssignmentVersion() + 1,
            command.occurredAt()));
  }

  private void validateWorkItem(
      PriorAuthorityAggregate priorAuthority, UUID workItemId, long expectedAssignmentVersion) {
    if (priorAuthority.getPriorAuthorityId() == null
        || !priorAuthority.getPriorAuthorityId().equals(workItemId)) {
      throw new ResourceNotFoundException(
          "No prior-authority work item found with id: " + workItemId);
    }
    if (expectedAssignmentVersion != priorAuthority.getAssignmentVersion()) {
      throw new WorkItemAssignmentConflictException(workItemId, "the assignment version is stale");
    }
  }
}
