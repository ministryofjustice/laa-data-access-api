package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDraftStoreTest {

  @Mock private PriorAuthorityDraftRepository repository;

  @InjectMocks private PriorAuthorityDraftStore store;

  @Test
  void givenNoExistingDraft_whenUpsert_thenInsertsRowAndReturnsFingerprint() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content = new PriorAuthorityContent("EXPERT", null, null, null, null);
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, "request-json", occurredAt);

    when(repository.findById(priorAuthorityId)).thenReturn(Optional.empty());

    String fingerprint =
        store.upsert(priorAuthorityId, applicationId, payload, "request-json", occurredAt);

    assertThat(fingerprint).isEqualTo(PayloadFingerprint.compute("request-json")).hasSize(64);
    ArgumentCaptor<PriorAuthorityDraft> captor = ArgumentCaptor.forClass(PriorAuthorityDraft.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(captor.getValue().getApplicationId()).isEqualTo(applicationId);
    assertThat(captor.getValue().getPayload()).isEqualTo(payload);
    assertThat(captor.getValue().getPayloadHash()).isEqualTo(fingerprint);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(occurredAt);
    assertThat(captor.getValue().getUpdatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenExistingDraft_whenUpsert_thenPreservesOriginalCreatedAtAndUpdatesUpdatedAt() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant originalCreatedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant secondOccurredAt = Instant.parse("2026-08-02T11:00:00Z");
    PriorAuthorityDraft existing =
        PriorAuthorityDraft.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .createdAt(originalCreatedAt)
            .updatedAt(originalCreatedAt)
            .build();
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, null, "second-request", secondOccurredAt);

    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(existing));

    store.upsert(priorAuthorityId, applicationId, payload, "second-request", secondOccurredAt);

    ArgumentCaptor<PriorAuthorityDraft> captor = ArgumentCaptor.forClass(PriorAuthorityDraft.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
    assertThat(captor.getValue().getUpdatedAt()).isEqualTo(secondOccurredAt);
  }

  @Test
  void givenStoredDraft_whenGet_thenReturnsPayload() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataPayload expectedPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, UUID.randomUUID(), null, "req", Instant.now());
    when(repository.findById(priorAuthorityId))
        .thenReturn(Optional.of(PriorAuthorityDraft.builder().payload(expectedPayload).build()));

    PriorAuthorityDataPayload result = store.get(priorAuthorityId);

    assertThat(result).isEqualTo(expectedPayload);
  }

  @Test
  void givenNoDraft_whenGet_thenThrowsIllegalStateException() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.get(priorAuthorityId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No draft found for submission: " + priorAuthorityId);
  }

  @Test
  void whenDelete_thenDelegatesToRepositoryDeleteById() {
    UUID priorAuthorityId = UUID.randomUUID();

    store.delete(priorAuthorityId);

    verify(repository).deleteById(eq(priorAuthorityId));
  }
}
