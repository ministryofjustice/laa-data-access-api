package uk.gov.justice.laa.dstew.access.command.application.assignment;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

/** Dispatches an unassign-caseworker command with a single retry on concurrent-write failures. */
@Component
public class UnassignCaseworkerUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public UnassignCaseworkerUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the application aggregate. */
  public void execute(UnassignCaseworkerFromApplicationCommand command) {
    dispatcher.dispatch(command);
  }
}
