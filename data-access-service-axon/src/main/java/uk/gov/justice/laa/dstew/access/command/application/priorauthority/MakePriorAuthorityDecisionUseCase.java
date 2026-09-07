package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a prior-authority decision command. */
@Component
public class MakePriorAuthorityDecisionUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public MakePriorAuthorityDecisionUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command to the prior-authority aggregate. */
  @AllowApiCaseworker
  public void execute(MakePriorAuthorityDecisionCommand command) {
    dispatcher.dispatch(command);
  }
}
