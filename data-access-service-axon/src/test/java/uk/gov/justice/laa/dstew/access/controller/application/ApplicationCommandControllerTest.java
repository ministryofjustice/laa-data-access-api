package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.assignment.AssignCaseworkerService;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;

/**
 * Focused unit coverage for {@link ApplicationCommandController#dispatchWithRetry}.
 *
 * <p>Projection-wait semantics are covered by {@code SubscriptionProjectionGatewayTest}.
 */
class ApplicationCommandControllerTest {

  private CommandGateway commandGateway;
  private ApplicationCommandController controller;

  @BeforeEach
  void setUp() {
    commandGateway = mock(CommandGateway.class);
    controller =
        new ApplicationCommandController(
            commandGateway,
            mock(SubscriptionProjectionGateway.class),
            mock(CreateApplicationCommandMapper.class),
            mock(MakeDecisionCommandMapper.class),
            mock(AssignCaseworkerService.class),
            mock(AssignCaseworkerRequestMapper.class),
            mock(UnassignCaseworkerRequestMapper.class),
            mock(CreateNoteCommandMapper.class));
  }

  @Test
  void givenSuccessfulDispatch_whenDispatchWithRetry_thenSendsOnce() {
    CreateApplicationCommand command = stubCommand();

    controller.dispatchWithRetry(command);

    verify(commandGateway, times(1)).sendAndWait(command);
  }

  @Test
  void givenConcurrencyException_whenDispatchWithRetry_thenRetriesOnce() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(new ConcurrencyException("concurrent write"))
        .thenReturn(null);

    controller.dispatchWithRetry(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenUniqueConstraintViolation_whenDispatchWithRetry_thenRetriesOnce() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(
            new DataIntegrityViolationException(
                "concurrent data version", new SQLException("duplicate key", "23505")))
        .thenReturn(null);

    controller.dispatchWithRetry(command);

    verify(commandGateway, times(2)).sendAndWait(command);
  }

  @Test
  void givenOtherDataIntegrityViolation_whenDispatchWithRetry_thenPropagatesWithoutRetry() {
    CreateApplicationCommand command = stubCommand();
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException(
            "foreign key violation", new SQLException("missing reference", "23503"));
    when(commandGateway.sendAndWait(command)).thenThrow(failure);

    assertThatThrownBy(() -> controller.dispatchWithRetry(command)).isSameAs(failure);
    verify(commandGateway).sendAndWait(command);
  }

  @Test
  void givenNonConcurrencyException_whenDispatchWithRetry_thenPropagatesWithoutRetry() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(new ResourceNotFoundException("no lead application"));

    assertThatThrownBy(() -> controller.dispatchWithRetry(command))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(commandGateway, times(1)).sendAndWait(command);
  }

  @Test
  void givenRetryAlsoFails_whenDispatchWithRetry_thenRethrowsWithOriginalSuppressed() {
    CreateApplicationCommand command = stubCommand();
    ConcurrencyException first = new ConcurrencyException("first write");
    ResourceNotFoundException retry = new ResourceNotFoundException("retry failure");
    when(commandGateway.sendAndWait(command)).thenThrow(first).thenThrow(retry);

    assertThatThrownBy(() -> controller.dispatchWithRetry(command))
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
