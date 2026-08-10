package uk.gov.justice.laa.dstew.access.command.application.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class CreateNoteUseCaseTest {

  private CommandGateway commandGateway;
  private CreateNoteUseCase useCase;

  @BeforeEach
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    useCase = new CreateNoteUseCase(commandGateway);
  }

  @Test
  void givenSuccessfulDispatch_whenExecute_thenSendsOnce() {
    CreateNoteCommand command = stubCommand();

    useCase.execute(command);

    verify(commandGateway, times(1)).sendAndWait(command);
  }

  @Test
  void givenConcurrencyException_whenExecute_thenRetriesOnce() {
    CreateNoteCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(new ConcurrencyException("concurrent write"))
        .thenReturn(null);

    useCase.execute(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenUniqueConstraintViolation_whenExecute_thenRetriesOnce() {
    CreateNoteCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key", new SQLException("duplicate key value", "23505")))
        .thenReturn(null);

    useCase.execute(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenNonRetryableException_whenExecute_thenPropagatesWithoutRetry() {
    CreateNoteCommand command = stubCommand();
    ResourceNotFoundException failure = new ResourceNotFoundException("not found");
    when(commandGateway.sendAndWait(command)).thenThrow(failure);

    assertThatThrownBy(() -> useCase.execute(command)).isSameAs(failure);
    verify(commandGateway, times(1)).sendAndWait(command);
  }

  @Test
  void givenRetryAlsoFails_whenExecute_thenRethrowsWithOriginalSuppressed() {
    CreateNoteCommand command = stubCommand();
    ConcurrencyException first = new ConcurrencyException("first");
    ResourceNotFoundException retry = new ResourceNotFoundException("retry failure");
    when(commandGateway.sendAndWait(command)).thenThrow(first).thenThrow(retry);

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(e -> assertThat(e.getSuppressed()).contains(first));
  }

  private CreateNoteCommand stubCommand() {
    return new CreateNoteCommand(UUID.randomUUID(), "note text", "{}", Instant.now());
  }
}
