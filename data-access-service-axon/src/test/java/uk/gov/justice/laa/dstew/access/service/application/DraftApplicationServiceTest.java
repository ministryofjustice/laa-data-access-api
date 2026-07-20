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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.PutDraftApplicationCommand;
import uk.gov.justice.laa.dstew.access.exception.ConflictException;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRecord;
import uk.gov.justice.laa.dstew.access.query.draft.DraftRepository;

class DraftApplicationServiceTest {

  private final DraftRepository repository = mock(DraftRepository.class);
  private final CommandGateway commandGateway = mock(CommandGateway.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);

  private final DraftApplicationService service =
      new DraftApplicationService(repository, commandGateway, clock);

  @Test
  void givenNewApplication_whenPutDraft_thenDispatchesPointerAndStoresBody() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("laaReference", "LAA-DRAFT-1");
    when(repository.findById(applicationId)).thenReturn(Optional.empty());

    service.putDraft(applicationId, content);

    ArgumentCaptor<PutDraftApplicationCommand> commandCaptor =
        ArgumentCaptor.forClass(PutDraftApplicationCommand.class);
    verify(commandGateway).sendAndWait(commandCaptor.capture());
    assertThat(commandCaptor.getValue().applyApplicationId()).isEqualTo(applicationId);

    ArgumentCaptor<DraftRecord> recordCaptor = ArgumentCaptor.forClass(DraftRecord.class);
    verify(repository).save(recordCaptor.capture());
    DraftRecord saved = recordCaptor.getValue();
    assertThat(saved.getApplyApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getContent()).isEqualTo(content);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
    assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
  }

  @Test
  void givenExistingDraft_whenPutDraft_thenOverwritesBodyPreservingCreatedAt() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> updated = Map.of("laaReference", "LAA-DRAFT-2");
    when(repository.findById(applicationId))
        .thenReturn(
            Optional.of(
                DraftRecord.builder()
                    .applyApplicationId(applicationId)
                    .content(Map.of("laaReference", "LAA-DRAFT-1"))
                    .createdAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .updatedAt(Instant.parse("2026-07-14T08:00:00Z"))
                    .build()));

    service.putDraft(applicationId, updated);

    ArgumentCaptor<DraftRecord> recordCaptor = ArgumentCaptor.forClass(DraftRecord.class);
    verify(repository).save(recordCaptor.capture());
    DraftRecord saved = recordCaptor.getValue();
    assertThat(saved.getContent()).isEqualTo(updated);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-07-14T08:00:00Z"));
    assertThat(saved.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-15T08:00:00Z"));
  }

  @Test
  void givenSubmittedApplication_whenPutDraft_thenRejectedAndBodyUntouched() {
    UUID applicationId = UUID.randomUUID();
    when(commandGateway.sendAndWait(any())).thenThrow(new ConflictException("already submitted"));

    assertThatThrownBy(() -> service.putDraft(applicationId, Map.of("laaReference", "LAA")))
        .isInstanceOf(ConflictException.class);

    verify(repository, never()).save(any());
  }
}
