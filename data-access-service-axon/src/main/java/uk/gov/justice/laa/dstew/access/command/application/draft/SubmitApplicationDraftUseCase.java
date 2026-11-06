package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a submit-application-draft command. */
@Component
public class SubmitApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public SubmitApplicationDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command; the Application is readable from its aggregate once it commits. */
  @AllowApiCaseworker
  public void submit(SubmitApplicationDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
