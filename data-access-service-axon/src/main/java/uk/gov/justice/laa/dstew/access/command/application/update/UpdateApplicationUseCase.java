package uk.gov.justice.laa.dstew.access.command.application.update;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches an update-application command with a single retry on concurrent-write failures. */
@Component
public class UpdateApplicationUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public UpdateApplicationUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the application aggregate. */
  @AllowApiCaseworker
  public void execute(UpdateApplicationCommand command) {
    dispatcher.dispatch(command);
  }
}
