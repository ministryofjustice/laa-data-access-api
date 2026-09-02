package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a save-prior-authority-draft command. Draft content is written synchronously to the
 * draft store within the command handler, so no projection wait is required before it is readable.
 */
@Component
public class SavePriorAuthorityDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public SavePriorAuthorityDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the save-draft command to create a new draft submission. */
  @AllowApiCaseworker
  public boolean create(SavePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(command);
    return true;
  }

  /** Dispatches the save-draft command to update an existing draft submission. */
  @AllowApiCaseworker
  public void update(SavePriorAuthorityDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
