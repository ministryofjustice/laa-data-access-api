package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class UnassignCaseworkerServiceTest {

  private EventStore eventStore;
  private CommandGateway commandGateway;
  private UnassignCaseworkerService service;

  @BeforeEach
  void setUp() {
    eventStore = mock(EventStore.class);
    commandGateway = mock(CommandGateway.class);
    service = new UnassignCaseworkerService(eventStore, commandGateway);
  }

  @Test
  void givenKnownApplication_whenUnassigned_thenDispatchesCommand() {
    UUID applicationId = UUID.randomUUID();
    DomainEventStream stream = mock(DomainEventStream.class);
    when(eventStore.readEvents(applicationId.toString())).thenReturn(stream);
    when(stream.hasNext()).thenReturn(true);
    Instant before = Instant.now();

    service.unassign(applicationId, "request", "description");

    ArgumentCaptor<UnassignCaseworkerFromApplicationCommand> captor =
        ArgumentCaptor.forClass(UnassignCaseworkerFromApplicationCommand.class);
    verify(commandGateway).sendAndWait(captor.capture());
    assertThat(captor.getValue())
        .satisfies(
            command -> {
              assertThat(command.applicationId()).isEqualTo(applicationId);
              assertThat(command.serialisedRequest()).isEqualTo("request");
              assertThat(command.eventDescription()).isEqualTo("description");
              assertThat(command.occurredAt()).isBetween(before, Instant.now());
            });
  }

  @Test
  void givenUnknownApplication_whenUnassigned_thenReturnsNotFoundWithoutDispatching() {
    UUID applicationId = UUID.randomUUID();
    DomainEventStream stream = mock(DomainEventStream.class);
    when(eventStore.readEvents(applicationId.toString())).thenReturn(stream);
    when(stream.hasNext()).thenReturn(false);

    assertThatThrownBy(() -> service.unassign(applicationId, "{}", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No application found with id: " + applicationId);

    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }
}
