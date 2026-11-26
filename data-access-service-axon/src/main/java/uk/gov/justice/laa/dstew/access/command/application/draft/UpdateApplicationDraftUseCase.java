package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a v2 update-draft command and returns the resulting draft version. */
@Component
public class UpdateApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public UpdateApplicationDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the update command and returns the draft version after the write. */
  @AllowApiCaseworker
  public long execute(UpdateApplicationDraftCommand command) {
    return dispatcher.dispatch(command, Long.class);
  }
}
