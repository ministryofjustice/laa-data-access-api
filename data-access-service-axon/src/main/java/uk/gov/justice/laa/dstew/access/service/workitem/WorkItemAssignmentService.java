package uk.gov.justice.laa.dstew.access.service.workitem;

import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.workallocation.AllocationId;
import uk.gov.justice.laa.dstew.access.command.workallocation.AssignCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.command.workallocation.UnassignCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkItemRepository;

/**
 * Application-layer entry point for assigning/unassigning caseworkers to work items.
 *
 * <p>Assignment is modelled as its own {@code WorkAllocation} aggregate keyed by the work item's
 * natural id, so this service does not need to know or care whether the work item is an application
 * or a prior authority: it dispatches a single {@code workItemId}-keyed command regardless. The
 * existence/allocatability of the work item is a non-transactional precondition, checked here
 * against the {@code work_items} read model (which only contains post-submission rows), giving the
 * 404. The one-active-caseworker invariant is enforced transactionally on the allocation stream.
 */
@Service
public class WorkItemAssignmentService {

  private final WorkItemRepository repository;
  private final CommandGateway commandGateway;

  /** Wires the work-item read model and the command gateway. */
  public WorkItemAssignmentService(WorkItemRepository repository, CommandGateway commandGateway) {
    this.repository = repository;
    this.commandGateway = commandGateway;
  }

  /**
   * Assigns a caseworker to the work item.
   *
   * @param workItemId the work item to assign
   * @param caseworkerId the caseworker to assign it to
   */
  public void assign(UUID workItemId, UUID caseworkerId) {
    requireExists(workItemId);
    commandGateway.sendAndWait(
        new AssignCaseworkerCommand(
            AllocationId.forWorkItem(workItemId), workItemId, caseworkerId));
  }

  /**
   * Clears the caseworker assignment on the work item.
   *
   * @param workItemId the work item to unassign
   */
  public void unassign(UUID workItemId) {
    requireExists(workItemId);
    try {
      commandGateway.sendAndWait(
          new UnassignCaseworkerCommand(AllocationId.forWorkItem(workItemId), workItemId));
    } catch (AggregateNotFoundException ex) {
      throw new ConflictException("Work item " + workItemId + " is not assigned to a caseworker");
    }
  }

  private void requireExists(UUID workItemId) {
    if (!repository.existsById(workItemId)) {
      throw new ResourceNotFoundException("No work item found with ID: " + workItemId);
    }
  }
}
