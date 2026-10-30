package uk.gov.justice.laa.dstew.access.command.application.ready;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.assignment.InheritLinkedGroupAssignmentCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;

/** Coordinates manual-assessment readiness with assignment inheritance from a linked group. */
@Service
@RequiredArgsConstructor
public class MarkApplicationReadyCoordinator {
  private final RetryingCommandDispatcher dispatcher;
  private final ApplicationGroupRouteResolver groupRouteResolver;

  /**
   * Records readiness and, inside a linked group, lets the application inherit the group's
   * caseworker. The HTTP endpoint also permits the auto-grant service role, so method security
   * preserves that existing caller while requiring a named caseworker for delegated requests.
   */
  @Transactional
  @PreAuthorize(
      "(@entra.hasAppRole('LAA_CASEWORKER') && @entra.hasName()) "
          + "|| @entra.hasAppRole('LAA_DECIDE_AUTO_GRANT')")
  public ReadyApplicationResult handle(MarkApplicationReadyCommand command) {
    Optional<UUID> groupId =
        groupRouteResolver.lockGroupRoutesForReadiness(command.applicationId());
    ReadyApplicationResult result = dispatcher.dispatch(command, ReadyApplicationResult.class);
    if (result == ReadyApplicationResult.RECORDED && groupId.isPresent()) {
      dispatcher.dispatch(
          new InheritLinkedGroupAssignmentCommand(
              groupId.get(), command.applicationId(), command.occurredAt()));
    }
    return result;
  }
}
