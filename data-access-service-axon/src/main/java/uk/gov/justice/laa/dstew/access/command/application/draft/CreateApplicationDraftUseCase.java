package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/**
 * Dispatches a create-application-draft command. Draft content is written synchronously to the
 * draft store within the command handler, so no projection wait is required before it is readable.
 */
@Component
public class CreateApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreateApplicationDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the create-draft command to start a new draft submission. */
  @AllowApiCaseworker
  public boolean execute(CreateApplicationDraftCommand command) {
    dispatcher.dispatch(command);
    return true;
  }
}
