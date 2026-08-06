package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ValidateApplicationExistsCommand;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.command.workitem.AssignWorkItemCommand;
import uk.gov.justice.laa.dstew.access.command.workitem.WorkItemId;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class AssignCaseworkerServiceTest {

  private CaseworkerRepository caseworkerRepository;
  private CommandGateway commandGateway;
  private AssignCaseworkerService service;

  @BeforeEach
  void setUp() {
    caseworkerRepository = mock(CaseworkerRepository.class);
    commandGateway = mock(CommandGateway.class);
    service = new AssignCaseworkerService(caseworkerRepository, commandGateway);
  }

  @Test
  void givenKnownCaseworkerAndApplication_whenAssigned_thenValidatesAndDispatchesRootWorkItemCommand() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    when(caseworkerRepository.existsById(caseworkerId)).thenReturn(true);
    final Instant before = Instant.now();

    service.assign(caseworkerId, applicationId, "request", "description");

    ArgumentCaptor<AssignWorkItemCommand> captor = ArgumentCaptor.forClass(AssignWorkItemCommand.class);
    InOrder commands = inOrder(commandGateway);
    commands.verify(commandGateway).sendAndWait(new ValidateApplicationExistsCommand(applicationId));
    commands.verify(commandGateway).sendAndWait(captor.capture());
    assertThat(captor.getValue())
        .satisfies(
            command -> {
              assertThat(command.workItemId()).isEqualTo(WorkItemId.toAggregateId(applicationId));
              assertThat(command.caseworkerId()).isEqualTo(caseworkerId);
              assertThat(command.serialisedRequest()).isEqualTo("request");
              assertThat(command.eventDescription()).isEqualTo("description");
              assertThat(command.fanOut()).isFalse();
              assertThat(command.occurredAt()).isBetween(before, Instant.now());
            });
  }

  @Test
  void givenUnknownCaseworker_whenAssigned_thenReturnsNotFoundWithoutDispatching() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    assertThatThrownBy(() -> service.assign(caseworkerId, applicationId, "{}", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No caseworker found with id: " + caseworkerId);

    verify(commandGateway, never()).sendAndWait(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void givenMissingApplication_whenAssigned_thenReturnsNotFoundWithoutAssigningWorkItem() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ResourceNotFoundException failure =
        new ResourceNotFoundException("No application found with id: " + applicationId);
    when(caseworkerRepository.existsById(caseworkerId)).thenReturn(true);
    doThrow(failure)
        .when(commandGateway)
        .sendAndWait(new ValidateApplicationExistsCommand(applicationId));

    assertThatThrownBy(() -> service.assign(caseworkerId, applicationId, "{}", null))
        .isSameAs(failure);

    verify(commandGateway).sendAndWait(new ValidateApplicationExistsCommand(applicationId));
    verify(commandGateway, never())
        .sendAndWait(org.mockito.ArgumentMatchers.any(AssignWorkItemCommand.class));
  }
}
