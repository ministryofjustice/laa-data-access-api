package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.ConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;

class CreateApplicationUseCaseTest {

  private CommandGateway commandGateway;
  private SubscriptionProjectionGateway projectionGateway;
  private CreateApplicationUseCase useCase;

  @BeforeEach
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    projectionGateway = mock(SubscriptionProjectionGateway.class);
    useCase = new CreateApplicationUseCase(commandGateway, projectionGateway);
  }

  @Test
  void givenProjectionConfirmed_whenExecute_thenReturnsTrue() {
    CreateApplicationCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(ApplicationReadModel.class), any()))
        .thenReturn(true);

    boolean result = useCase.execute(command);

    assertThat(result).isTrue();
    verify(projectionGateway)
        .awaitProjection(
            eq(new FindApplicationByIdQuery(command.applicationId())),
            eq(ApplicationReadModel.class),
            any());
  }

  @Test
  void givenProjectionTimeout_whenExecute_thenReturnsFalse() {
    CreateApplicationCommand command = stubCommand();
    when(projectionGateway.awaitProjection(any(), eq(ApplicationReadModel.class), any()))
        .thenReturn(false);

    boolean result = useCase.execute(command);

    assertThat(result).isFalse();
  }

  @Test
  void givenSuccessfulDispatch_whenExecuteViaAction_thenSendsCommandOnce() {
    CreateApplicationCommand command = stubCommand();
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(ApplicationReadModel.class), any());

    useCase.execute(command);

    verify(commandGateway, times(1)).sendAndWait(command);
  }

  @Test
  void givenConcurrencyException_whenExecuteViaAction_thenRetriesOnce() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(new ConcurrencyException("concurrent write"))
        .thenReturn(null);
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(ApplicationReadModel.class), any());

    useCase.execute(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenUniqueConstraintViolation_whenExecuteViaAction_thenRetriesOnce() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(
            new DataIntegrityViolationException(
                "duplicate key", new SQLException("duplicate key value", "23505")))
        .thenReturn(null);
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(ApplicationReadModel.class), any());

    useCase.execute(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenRetryAlsoFails_whenExecuteViaAction_thenRethrowsWithOriginalSuppressed() {
    CreateApplicationCommand command = stubCommand();
    ConcurrencyException first = new ConcurrencyException("first");
    ResourceNotFoundException retry = new ResourceNotFoundException("retry failure");
    when(commandGateway.sendAndWait(command)).thenThrow(first).thenThrow(retry);
    doAnswer(
            invocation -> {
              Runnable action = invocation.getArgument(2);
              action.run();
              return true;
            })
        .when(projectionGateway)
        .awaitProjection(any(), eq(ApplicationReadModel.class), any());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(e -> assertThat(e.getSuppressed()).contains(first));
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
