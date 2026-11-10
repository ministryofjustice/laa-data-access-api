package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import java.util.List;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssignmentConflictException;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.AssignedWorkItems;
import uk.gov.justice.laa.dstew.access.command.worklist.assign.DirectPriorAuthorityWorkItemAssignmentCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Handles assignment of prior-authority work items to caseworkers. */
@Component
public class DirectPriorAuthorityWorkItemAssignmentCommandHandler {

  /**
   * Assigns a prior-authority work item to a caseworker, emits the assignment event and returns the
   * item with its parent application's LAA reference.
   */
  @CommandHandler
  public AssignedWorkItems handle(
      DirectPriorAuthorityWorkItemAssignmentCommand command,
      @InjectEntity(idProperty = "workItemId") PriorAuthorityAggregate priorAuthority,
      ApplicationDataStore applicationDataStore,
      EventAppender eventAppender) {

    validateWorkItem(priorAuthority, command.workItemId(), command.expectedAssignmentVersion());

    if (priorAuthority.getCaseworkerId() != null) {
      throw new WorkItemAssignmentConflictException(command.workItemId(), "it is already assigned");
    }

    eventAppender.append(
        new WorkItemAssigned(
            command.workItemId(),
            WorkItemType.PRIOR_AUTHORITY,
            priorAuthority.getDataVersion(),
            priorAuthority.getAssignmentVersion() + 1,
            command.caseworkerId(),
            command.occurredAt()));

    UUID parentApplicationId = priorAuthority.getApplicationId();
    String laaReference =
        applicationDataStore
            .get(parentApplicationId, applicationDataStore.latestVersion(parentApplicationId))
            .laaReference();
    return new AssignedWorkItems(
        List.of(new AssignedWorkItems.AssignedItem(command.workItemId(), laaReference)));
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
