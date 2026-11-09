package uk.gov.justice.laa.dstew.access.command.application.draft;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a create-application-draft command. */
@Component
public class CreateApplicationDraftUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreateApplicationDraftUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command; the Application is readable from its aggregate once it commits. */
  @AllowApiCaseworker
  public void execute(CreateApplicationDraftCommand command) {
    dispatcher.dispatch(command);
  }
}
