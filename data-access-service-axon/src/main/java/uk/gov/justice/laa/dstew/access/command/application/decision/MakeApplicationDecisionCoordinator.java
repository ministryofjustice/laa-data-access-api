package uk.gov.justice.laa.dstew.access.command.application.decision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.ValidateLinkedGroupAssigneeCommand;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRoute;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteKind;
import uk.gov.justice.laa.dstew.access.command.worklist.route.WorkItemRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Coordinates a decision with the linked group that owns the application's assignment. */
@Service
@RequiredArgsConstructor
public class MakeApplicationDecisionCoordinator {
  private final RetryingCommandDispatcher dispatcher;
  private final WorkItemRouteResolver workItemRouteResolver;

  /**
   * Dispatches a decision, first validating the caseworker against the linked group that owns the
   * application's assignment.
   */
  @Transactional
  @AllowApiCaseworker
  public void handle(MakeApplicationDecisionCommand command) {
    var route = workItemRouteResolver.lockRouteIfPresent(command.applicationId());
    if (route.isEmpty() || route.get().getRouteKind() != WorkItemRouteKind.LINKED_GROUP) {
      dispatcher.dispatch(command);
      return;
    }
    WorkItemRoute linkedRoute = route.get();
    dispatcher.dispatch(
        new ValidateLinkedGroupAssigneeCommand(
            linkedRoute.getGroupId(), command.applicationId(), command.caseworkerId()));
    dispatcher.dispatch(command.withGroupAssigneeValidatedFor(linkedRoute.getGroupId()));
  }
}
