package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a create-prior-authority-draft command. Draft content is written synchronously to the
 * draft store within the command handler, so no projection wait is required before it is readable.
 */
@Component
public class CreatePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreatePriorAuthorityDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /**
   * Validates the application is granted, then dispatches the create-draft command to create a new
   * draft submission.
   *
   * @throws RuntimeException propagated from validation if the application is not granted
   */
  @AllowApiCaseworker
  public boolean execute(CreatePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(command.applicationId()));
    dispatcher.dispatch(command);
    return true;
  }
}
