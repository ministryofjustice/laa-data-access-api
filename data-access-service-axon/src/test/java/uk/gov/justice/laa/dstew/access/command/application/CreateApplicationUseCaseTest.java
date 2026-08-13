package uk.gov.justice.laa.dstew.access.command.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;

class CreateApplicationUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private ApplicationEventStorePositionRepository eventStorePositionRepository;
  private CreateApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    eventStorePositionRepository = mock(ApplicationEventStorePositionRepository.class);
    useCase = new CreateApplicationUseCase(dispatcher, eventStorePositionRepository);
  }

  @Test
  void givenCommittedCommand_whenExecute_thenReturnsAggregateSequence() {
    CreateApplicationCommand command = stubCommand();
    when(dispatcher.dispatchAsync(command)).thenReturn(CompletableFuture.completedFuture(null));
    when(eventStorePositionRepository.latestSequence(command.applicationId())).thenReturn(3L);

    ApplicationCommandResult result = useCase.execute(command).join();

    org.assertj.core.api.Assertions.assertThat(result)
        .isEqualTo(new ApplicationCommandResult(command.applicationId(), 3L));
  }

  @Test
  void givenCommittedCommand_whenExecute_thenReadsCommittedSequenceAfterDispatch() {
    CreateApplicationCommand command = stubCommand();
    when(dispatcher.dispatchAsync(command)).thenReturn(CompletableFuture.completedFuture(null));
    when(eventStorePositionRepository.latestSequence(command.applicationId())).thenReturn(0L);

    useCase.execute(command).join();

    verify(dispatcher).dispatchAsync(command);
    verify(eventStorePositionRepository).latestSequence(command.applicationId());
  }

  private CreateApplicationCommand stubCommand() {
    UUID id = UUID.randomUUID();
    return new CreateApplicationCommand(
        id,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", id.toString()),
        List.of(),
        "{}",
        1,
        "ApplyApplication.json",
        "APPLY");
  }
}
