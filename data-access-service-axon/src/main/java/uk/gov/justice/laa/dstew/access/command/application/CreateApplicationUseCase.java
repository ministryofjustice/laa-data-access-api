package uk.gov.justice.laa.dstew.access.command.application;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

/** Dispatches a create-application command to Axon. */
@Component
public class CreateApplicationUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ApplicationEventStorePositionRepository eventStorePositionRepository;

  public CreateApplicationUseCase(
      RetryingCommandDispatcher dispatcher,
      ApplicationEventStorePositionRepository eventStorePositionRepository) {
    this.dispatcher = dispatcher;
    this.eventStorePositionRepository = eventStorePositionRepository;
  }

  /** Dispatches the command and completes when Axon has handled it. */
  public CompletableFuture<ApplicationCommandResult> execute(CreateApplicationCommand command) {
    return dispatcher
        .dispatchAsync(command)
        .thenApply(
            ignored ->
                new ApplicationCommandResult(
                    command.applicationId(),
                    eventStorePositionRepository.latestSequence(command.applicationId())));
  }
}
