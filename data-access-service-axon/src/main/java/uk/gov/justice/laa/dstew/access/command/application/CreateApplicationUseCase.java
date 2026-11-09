package uk.gov.justice.laa.dstew.access.command.application;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a create-application command. */
@Component
public class CreateApplicationUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public CreateApplicationUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches the command; the Application is readable from its aggregate once it commits. */
  @AllowApiCaseworker
  public void execute(CreateApplicationCommand command) {
    dispatcher.dispatch(command);
  }
}
