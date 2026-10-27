package uk.gov.justice.laa.dstew.access.command.application.ready;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

/** Locks the linked group (if any) and records manual-assessment readiness in one transaction. */
@Service
@RequiredArgsConstructor
public class MarkApplicationReadyCoordinator {
  private final ApplicationGroupRouteResolver groupRouteResolver;
  private final RetryingCommandDispatcher dispatcher;

  /** Records readiness; a grouped application takes its group's assignment in the same command. */
  @Transactional
  public ReadyApplicationResult handle(MarkApplicationReadyCommand command) {
    var groupId = groupRouteResolver.lockForAssignment(command.applicationId());
    return dispatcher.dispatch(
        command.withLinkedGroupId(groupId.orElse(null)), ReadyApplicationResult.class);
  }
}
