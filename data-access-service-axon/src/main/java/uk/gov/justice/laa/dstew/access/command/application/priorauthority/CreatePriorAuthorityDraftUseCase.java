package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidatePriorAuthorityGroupEligibilityCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.route.ApplicationGroupRouteResolver;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Validates application eligibility and dispatches a create-prior-authority-draft command, waiting
 * for the projection to confirm the draft is readable.
 */
@Component
public class CreatePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ApplicationGroupRouteResolver routeResolver;

  public CreatePriorAuthorityDraftUseCase(
      RetryingCommandDispatcher dispatcher, ApplicationGroupRouteResolver routeResolver) {
    this.dispatcher = dispatcher;
    this.routeResolver = routeResolver;
  }

  /**
   * Validates the application is granted, dispatches the create-draft command, and waits for the
   * projection to become readable.
   *
   * @return {@code true} when the projection confirms the submission within the configured timeout;
   *     {@code false} on timeout — the command has still committed.
   * @throws RuntimeException propagated from validation if the application is not granted
   */
  @AllowApiCaseworker
  @Transactional
  public boolean execute(CreatePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
    routeResolver
        .resolveGroupForPriorAuthorityCreation(command.applicationId())
        .ifPresent(
            groupId ->
                dispatcher.dispatch(
                    new ValidatePriorAuthorityGroupEligibilityCommand(
                        groupId, command.applicationId())));
    dispatcher.dispatch(command);
    return true;
  }
}
