package uk.gov.justice.laa.dstew.access.command.application.ready;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches an auto-grant outcome command with a single retry on concurrent-write failures. */
@Component
public class RecordAutoGrantOutcomeUseCase {

  private final RetryingCommandDispatcher dispatcher;

  public RecordAutoGrantOutcomeUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Dispatches an outcome that marks the application ready for manual assessment. */
  @AllowApiCaseworker
  public ReadyApplicationResult recordReady(MarkApplicationReadyCommand command) {
    return dispatcher.dispatch(command, ReadyApplicationResult.class);
  }

  /** Dispatches any other terminal auto-grant outcome command. */
  @AllowApiCaseworker
  public void record(Object command) {
    dispatcher.dispatch(command);
  }
}
