package uk.gov.justice.laa.dstew.access.command.application.ready;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

/**
 * Dispatches outcomes after starter-managed HTTP authorization, retrying concurrent writes once.
 */
@Component
@RequiredArgsConstructor
public class RecordAutoGrantOutcomeUseCase {
  private final RetryingCommandDispatcher dispatcher;
  private final MarkApplicationReadyCoordinator readyCoordinator;

  /** Dispatches an outcome that marks the application ready for manual assessment. */
  public ReadyApplicationResult recordReady(MarkApplicationReadyCommand command) {
    return readyCoordinator.handle(command);
  }

  /** Dispatches any other terminal auto-grant outcome command. */
  public void record(Object command) {
    dispatcher.dispatch(command);
  }
}
