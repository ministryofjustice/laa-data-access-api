package uk.gov.justice.laa.dstew.access.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateEntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.SubmitPriorAuthorityCommand;
import uk.gov.justice.laa.dstew.access.command.application.UpdatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;
import uk.gov.justice.laa.dstew.access.query.priorauthority.PriorAuthorityDraftRecord;
import uk.gov.justice.laa.dstew.access.query.priorauthority.PriorAuthorityDraftRepository;

class PriorAuthorityServiceTest {

  private final PriorAuthorityDraftRepository repository =
      mock(PriorAuthorityDraftRepository.class);
  private final CommandGateway commandGateway = mock(CommandGateway.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);

  private final PriorAuthorityService service =
      new PriorAuthorityService(repository, commandGateway, clock);

  @Test
  void givenContent_whenCreateDraft_thenStoresBodyAndDispatchesPointerCommand() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("reference", "PA-1", "amount", 500);

    UUID result = service.createDraft(applicationId, content);

    ArgumentCaptor<PriorAuthorityDraftRecord> recordCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDraftRecord.class);
    verify(repository).save(recordCaptor.capture());
    PriorAuthorityDraftRecord saved = recordCaptor.getValue();
    assertThat(saved.getPriorAuthorityId()).isEqualTo(result);
    assertThat(saved.getApplyApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getContent()).isEqualTo(content);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
    assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));

    ArgumentCaptor<CreatePriorAuthorityDraftCommand> commandCaptor =
        ArgumentCaptor.forClass(CreatePriorAuthorityDraftCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    CreatePriorAuthorityDraftCommand command = commandCaptor.getValue();
    assertThat(command.applyApplicationId()).isEqualTo(applicationId);
    assertThat(command.priorAuthorityId()).isEqualTo(result);
  }

  @Test
  void givenCommandRejected_whenCreateDraft_thenCleansOrphanBodyAndRethrows() {
    UUID applicationId = UUID.randomUUID();
    when(commandGateway.sendAndWait(any()))
        .thenThrow(new ConflictException("application not submitted"));

    assertThatThrownBy(() -> service.createDraft(applicationId, Map.of("reference", "PA-1")))
        .isInstanceOf(ConflictException.class);

    ArgumentCaptor<PriorAuthorityDraftRecord> recordCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDraftRecord.class);
    verify(repository).save(recordCaptor.capture());
    verify(repository).deleteById(recordCaptor.getValue().getPriorAuthorityId());
  }

  @Test
  void givenExistingDraft_whenUpdateDraft_thenDispatchesThenOverwritesBody() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    Map<String, Object> updated = Map.of("reference", "PA-1", "amount", 750);
    when(repository.findById(priorAuthorityId))
        .thenReturn(
            Optional.of(
                PriorAuthorityDraftRecord.builder()
                    .priorAuthorityId(priorAuthorityId)
                    .applyApplicationId(applicationId)
                    .content(Map.of("reference", "PA-1", "amount", 500))
                    .createdAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .updatedAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .build()));

    service.updateDraft(applicationId, priorAuthorityId, updated);

    ArgumentCaptor<UpdatePriorAuthorityDraftCommand> commandCaptor =
        ArgumentCaptor.forClass(UpdatePriorAuthorityDraftCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    assertThat(commandCaptor.getValue().priorAuthorityId()).isEqualTo(priorAuthorityId);

    ArgumentCaptor<PriorAuthorityDraftRecord> recordCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDraftRecord.class);
    verify(repository).save(recordCaptor.capture());
    PriorAuthorityDraftRecord saved = recordCaptor.getValue();
    assertThat(saved.getContent()).isEqualTo(updated);
    assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T08:00:00Z"));
  }

  @Test
  void givenTransitionRejected_whenUpdateDraft_thenDoesNotTouchBody() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    when(commandGateway.sendAndWait(any()))
        .thenThrow(new AggregateEntityNotFoundException("no such prior authority"));

    assertThatThrownBy(
            () -> service.updateDraft(applicationId, priorAuthorityId, Map.of("reference", "PA-1")))
        .isInstanceOf(AggregateEntityNotFoundException.class);

    verify(repository, never()).save(any());
  }

  @Test
  void whenSubmit_thenDispatchesSubmitCommand() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    service.submit(applicationId, priorAuthorityId);

    ArgumentCaptor<SubmitPriorAuthorityCommand> commandCaptor =
        ArgumentCaptor.forClass(SubmitPriorAuthorityCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    assertThat(commandCaptor.getValue().applyApplicationId()).isEqualTo(applicationId);
    assertThat(commandCaptor.getValue().priorAuthorityId()).isEqualTo(priorAuthorityId);
  }
}
