package uk.gov.justice.laa.dstew.access.service.workitem;

import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.AssignApplicationCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.command.application.AssignPriorAuthorityCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.command.application.UnassignApplicationCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.command.application.UnassignPriorAuthorityCaseworkerCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkItemRecord;
import uk.gov.justice.laa.dstew.access.query.workitem.WorkItemRepository;

/**
 * Application-layer entry point for the polymorphic assign/unassign of caseworkers to work items.
 *
 * <p>The API is polymorphic but the invariant is not: this service resolves the opaque {@code
 * workItemId} against the {@code work_items} read model to discover the owning aggregate (an
 * application or a prior authority member), then dispatches the aggregate-specific command. The
 * one-active-caseworker invariant is enforced by the aggregate/member, not here.
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
   * Assigns a caseworker to the work item, routing to the owning aggregate.
   *
   * @param workItemId the work item to assign
   * @param caseworkerId the caseworker to assign it to
   */
  public void assign(UUID workItemId, UUID caseworkerId) {
    WorkItemRecord item = require(workItemId);
    switch (item.getWorkType()) {
      case APPLICATION ->
          commandGateway.sendAndWait(
              new AssignApplicationCaseworkerCommand(item.getApplicationId(), caseworkerId));
      case PRIOR_AUTHORITY ->
          commandGateway.sendAndWait(
              new AssignPriorAuthorityCaseworkerCommand(
                  item.getApplicationId(), item.getPriorAuthorityId(), caseworkerId));
      default -> throw unknownWorkType(item);
    }
  }

  /**
   * Clears the caseworker assignment on the work item, routing to the owning aggregate.
   *
   * @param workItemId the work item to unassign
   */
  public void unassign(UUID workItemId) {
    WorkItemRecord item = require(workItemId);
    switch (item.getWorkType()) {
      case APPLICATION ->
          commandGateway.sendAndWait(
              new UnassignApplicationCaseworkerCommand(item.getApplicationId()));
      case PRIOR_AUTHORITY ->
          commandGateway.sendAndWait(
              new UnassignPriorAuthorityCaseworkerCommand(
                  item.getApplicationId(), item.getPriorAuthorityId()));
      default -> throw unknownWorkType(item);
    }
  }

  private static IllegalStateException unknownWorkType(WorkItemRecord item) {
    return new IllegalStateException("Unsupported work item type: " + item.getWorkType());
  }

  private WorkItemRecord require(UUID workItemId) {
    return repository
        .findById(workItemId)
        .orElseThrow(
            () -> new ResourceNotFoundException("No work item found with ID: " + workItemId));
  }
}
