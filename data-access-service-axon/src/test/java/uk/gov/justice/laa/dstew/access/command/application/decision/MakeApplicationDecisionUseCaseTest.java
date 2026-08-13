package uk.gov.justice.laa.dstew.access.command.application.decision;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationEventStorePositionRepository;

class MakeApplicationDecisionUseCaseTest {

  private RetryingCommandDispatcher dispatcher;
  private ApplicationEventStorePositionRepository eventStorePositionRepository;
  private MakeApplicationDecisionUseCase useCase;

  @BeforeEach
  void setUp() {
    dispatcher = mock(RetryingCommandDispatcher.class);
    eventStorePositionRepository = mock(ApplicationEventStorePositionRepository.class);
    useCase = new MakeApplicationDecisionUseCase(dispatcher, eventStorePositionRepository);
  }

  @Test
  void givenCommand_whenExecute_thenDelegatesToRetryingDispatcher() {
    MakeApplicationDecisionCommand command = stubCommand();

    org.mockito.Mockito.when(dispatcher.dispatchAsync(command))
        .thenReturn(CompletableFuture.completedFuture(null));
    org.mockito.Mockito.when(eventStorePositionRepository.latestSequence(command.applicationId()))
        .thenReturn(1L);

    useCase.execute(command).join();

    verify(dispatcher).dispatchAsync(command);
  }

  private MakeApplicationDecisionCommand stubCommand() {
    return new MakeApplicationDecisionCommand(
        UUID.randomUUID(), 0L, "GRANTED", false, List.of(), null, "{}", "decision", Instant.now());
  }
}
