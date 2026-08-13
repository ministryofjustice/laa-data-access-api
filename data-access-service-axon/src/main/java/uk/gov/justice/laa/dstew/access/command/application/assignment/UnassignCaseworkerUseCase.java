package uk.gov.justice.laa.dstew.access.command.application.assignment;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCommandResult;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEventStorePositionRepository;

/** Dispatches an unassign-caseworker command with a single retry on concurrent-write failures. */
@Component
public class UnassignCaseworkerUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ApplicationEventStorePositionRepository eventStorePositionRepository;

  public UnassignCaseworkerUseCase(
      RetryingCommandDispatcher dispatcher,
      ApplicationEventStorePositionRepository eventStorePositionRepository) {
    this.dispatcher = dispatcher;
    this.eventStorePositionRepository = eventStorePositionRepository;
  }

  /** Dispatches the command to the application aggregate. */
  public CompletableFuture<ApplicationCommandResult> execute(
      UnassignCaseworkerFromApplicationCommand command) {
    return dispatcher
        .dispatchAsync(command)
        .thenApply(
            ignored ->
                new ApplicationCommandResult(
                    command.applicationId(),
                    eventStorePositionRepository.latestSequence(command.applicationId())));
  }
}
