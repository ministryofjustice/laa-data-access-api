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
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class AssignCaseworkerServiceTest {

  private CaseworkerRepository caseworkerRepository;
  private EventStore eventStore;
  private CommandGateway commandGateway;
  private AssignCaseworkerService service;

  @BeforeEach
  void setUp() {
    caseworkerRepository = mock(CaseworkerRepository.class);
    eventStore = mock(EventStore.class);
    commandGateway = mock(CommandGateway.class);
    service = new AssignCaseworkerService(caseworkerRepository, eventStore, commandGateway);
  }

  @Test
  void givenKnownCaseworkerAndApplication_whenAssigned_thenDispatchesCommand() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    DomainEventStream stream = mock(DomainEventStream.class);
    when(caseworkerRepository.existsById(caseworkerId)).thenReturn(true);
    when(eventStore.readEvents(applicationId.toString())).thenReturn(stream);
    when(stream.hasNext()).thenReturn(true);
    Instant before = Instant.now();

    service.assign(caseworkerId, applicationId, "request", "description");

    ArgumentCaptor<AssignCaseworkerToApplicationCommand> captor =
        ArgumentCaptor.forClass(AssignCaseworkerToApplicationCommand.class);
    verify(commandGateway).sendAndWait(captor.capture());
    assertThat(captor.getValue())
        .satisfies(
            command -> {
              assertThat(command.applicationId()).isEqualTo(applicationId);
              assertThat(command.caseworkerId()).isEqualTo(caseworkerId);
              assertThat(command.serialisedRequest()).isEqualTo("request");
              assertThat(command.eventDescription()).isEqualTo("description");
              assertThat(command.occurredAt()).isBetween(before, Instant.now());
            });
  }

  @Test
  void givenUnknownCaseworker_whenAssigned_thenReturnsNotFoundWithoutReadingApplication() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    assertThatThrownBy(() -> service.assign(caseworkerId, applicationId, "{}", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No caseworker found with id: " + caseworkerId);

    verify(eventStore, never()).readEvents(applicationId.toString());
    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenUnknownApplication_whenAssigned_thenReturnsNotFoundWithoutDispatching() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    DomainEventStream stream = mock(DomainEventStream.class);
    when(caseworkerRepository.existsById(caseworkerId)).thenReturn(true);
    when(eventStore.readEvents(applicationId.toString())).thenReturn(stream);
    when(stream.hasNext()).thenReturn(false);

    assertThatThrownBy(() -> service.assign(caseworkerId, applicationId, "{}", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No application found with id: " + applicationId);

    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }
}
