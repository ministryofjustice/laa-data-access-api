package uk.gov.justice.laa.dstew.access.command.application.note;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a create-note command with a single retry on concurrent-write failures. */
@Component
public class CreateNoteUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreateNoteUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the application aggregate. */
  @AllowApiCaseworker
  public void execute(CreateNoteCommand command) {
    dispatcher.dispatch(command);
  }
}
