package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches an update-application-draft command. Draft content is written synchronously to the
 * draft store within the command handler, so no projection wait is required before it is readable.
 */
@Component
public class UpdateApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public UpdateApplicationDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the update-draft command to update an existing draft submission. */
  @AllowApiCaseworker
  public void execute(UpdateApplicationDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
