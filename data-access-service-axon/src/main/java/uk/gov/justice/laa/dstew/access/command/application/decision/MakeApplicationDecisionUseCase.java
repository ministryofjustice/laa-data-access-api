package uk.gov.justice.laa.dstew.access.command.application.decision;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

/** Dispatches a make-decision command with a single retry on concurrent-write failures. */
@Component
public class MakeApplicationDecisionUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public MakeApplicationDecisionUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the application aggregate. */
  public void execute(MakeApplicationDecisionCommand command) {
    dispatcher.dispatch(command);
  }
}
