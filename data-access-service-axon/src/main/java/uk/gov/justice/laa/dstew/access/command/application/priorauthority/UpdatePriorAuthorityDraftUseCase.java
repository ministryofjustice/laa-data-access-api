package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches an update-prior-authority-draft command. Draft content is written synchronously to the
 * draft store within the command handler, so no projection wait is required before it is readable.
 */
@Component
public class UpdatePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public UpdatePriorAuthorityDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the update-draft command to update an existing draft submission. */
  @AllowApiCaseworker
  public void execute(UpdatePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
