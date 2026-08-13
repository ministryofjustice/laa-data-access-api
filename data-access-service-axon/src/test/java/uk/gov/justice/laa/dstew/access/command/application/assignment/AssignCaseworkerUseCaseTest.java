package uk.gov.justice.laa.dstew.access.command.application.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class AssignCaseworkerUseCaseTest {

  private CaseworkerRepository caseworkerRepository;
  private RetryingCommandDispatcher dispatcher;
  private AssignCaseworkerUseCase useCase;

  @BeforeEach
  void setUp() {
    caseworkerRepository = mock(CaseworkerRepository.class);
    dispatcher = mock(RetryingCommandDispatcher.class);
    useCase = new AssignCaseworkerUseCase(caseworkerRepository, dispatcher);
  }

  @Test
  void givenKnownCaseworkerAndApplication_whenAssigned_thenDispatchesCommand() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    when(caseworkerRepository.existsById(caseworkerId)).thenReturn(true);
    Instant before = Instant.now();

    when(dispatcher.dispatchAsync(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    useCase.assign(caseworkerId, applicationId, "request", "description").join();

    ArgumentCaptor<AssignCaseworkerToApplicationCommand> captor =
        ArgumentCaptor.forClass(AssignCaseworkerToApplicationCommand.class);
    verify(dispatcher).dispatchAsync(captor.capture());
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
  void givenUnknownCaseworker_whenAssigned_thenReturnsNotFoundWithoutDispatching() {
    UUID caseworkerId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    assertThatThrownBy(() -> useCase.assign(caseworkerId, applicationId, "{}", null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No caseworker found with id: " + caseworkerId);

    verify(dispatcher, never()).dispatchAsync(org.mockito.ArgumentMatchers.any());
  }
}
