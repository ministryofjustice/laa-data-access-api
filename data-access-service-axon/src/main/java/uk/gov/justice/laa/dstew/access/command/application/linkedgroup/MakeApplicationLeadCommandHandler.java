package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCallerResource;
import uk.gov.justice.laa.dstew.access.security.RequireWriteAccess;
import uk.gov.justice.laa.dstew.access.security.WriteAccessResolverResource;

/** Secured transactional coordinator for making an application the lead of its linked group. */
@Service
@RequiredArgsConstructor
public class MakeApplicationLeadCommandHandler {
  private final ApplicationGroupRouteResolver routeResolver;
  private final RetryingCommandDispatcher dispatcher;

  /** Resolves and locks the linked group before dispatching its lead-change command. */
  @Transactional
  @AllowApiCaseworker
  @RequireWriteAccess(resolver = LinkedApplicationGroupWriteAccessResolver.class)
  public void handle(
      @AuthenticatedCallerResource @WriteAccessResolverResource
          MakeApplicationLeadCommand command) {
    var expectedGroup = command.expectedGroup();
    var groupId =
        routeResolver.resolveGroupForMutation(command.applicationId(), expectedGroup.groupId());
    dispatcher.dispatch(
        new ChangeLinkedGroupLeadCommand(
            groupId, command.applicationId(), expectedGroup.version(), command.occurredAt()));
  }
}
