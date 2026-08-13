package uk.gov.justice.laa.dstew.access.command.application.note;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCommandResult;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEventStorePositionRepository;

/** Dispatches a create-note command with a single retry on concurrent-write failures. */
@Component
public class CreateNoteUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ApplicationEventStorePositionRepository eventStorePositionRepository;

  public CreateNoteUseCase(
      RetryingCommandDispatcher dispatcher,
      ApplicationEventStorePositionRepository eventStorePositionRepository) {
    this.dispatcher = dispatcher;
    this.eventStorePositionRepository = eventStorePositionRepository;
  }

  /** Dispatches the command to the application aggregate. */
  public CompletableFuture<ApplicationCommandResult> execute(CreateNoteCommand command) {
    return dispatcher
        .dispatchAsync(command)
        .thenApply(
            ignored ->
                new ApplicationCommandResult(
                    command.applicationId(),
                    eventStorePositionRepository.latestSequence(command.applicationId())));
  }
}
