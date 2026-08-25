package uk.gov.justice.laa.dstew.access.command.application.decision;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a make-decision command with a single retry on concurrent-write failures. */
@Component
public class MakeApplicationDecisionUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public MakeApplicationDecisionUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the application aggregate. */
  @AllowApiCaseworker
  public void execute(MakeApplicationDecisionCommand command) {
    dispatcher.dispatch(command);
  }
}
