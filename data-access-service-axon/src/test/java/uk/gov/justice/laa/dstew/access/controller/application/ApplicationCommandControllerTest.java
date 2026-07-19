package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
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
            mock(MakeDecisionCommandMapper.class));
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
  void givenAggregateStreamCreationException_whenDispatchWithRetry_thenRetriesOnce() {
    CreateApplicationCommand command = stubCommand();
    when(commandGateway.sendAndWait(command))
        .thenThrow(new AggregateStreamCreationException("stream already exists"))
        .thenReturn(null);

    controller.dispatchWithRetry(command);

    verify(commandGateway, times(2)).sendAndWait(command);
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
