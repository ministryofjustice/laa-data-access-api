package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ReclaimAssignmentFromGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Secured transactional coordinator for removing an application from its linked group. */
@Service
@RequiredArgsConstructor
public class UnlinkApplicationCoordinator {
  private final ApplicationGroupRouteResolver routeResolver;
  private final WorkItemRouteResolver workItemRouteResolver;
  private final RetryingCommandDispatcher dispatcher;

  /** Resolves and locks the linked group before dispatching its removal command. */
  @Transactional
  @AllowApiCaseworker
  public void handle(UnlinkApplicationCommand command) {
    var expectedGroup = command.expectedGroup();
    var groupId =
        routeResolver.resolveGroupForMutation(command.applicationId(), expectedGroup.groupId());
    workItemRouteResolver.lockGroupRoutes(groupId);
    var removal =
        dispatcher.dispatch(
            new RemoveApplicationFromLinkedGroupCommand(
                groupId, command.applicationId(), expectedGroup.version(), command.occurredAt()),
            LinkedGroupRemovalResult.class);
    removal
        .leavingApplicationIds()
        .forEach(
            applicationId ->
                dispatcher.dispatch(
                    new ReclaimAssignmentFromGroupCommand(
                        applicationId,
                        groupId,
                        removal.caseworkerId(),
                        removal.assignmentVersion(),
                        command.occurredAt())));
  }
}
