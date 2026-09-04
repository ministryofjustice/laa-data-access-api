package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
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
  void givenStoredDraft_whenFind_thenReturnsPayload() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDataPayload expectedPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, UUID.randomUUID(), null, "req", Instant.now());
    when(repository.findById(priorAuthorityId))
        .thenReturn(Optional.of(PriorAuthorityDraft.builder().payload(expectedPayload).build()));

    Optional<PriorAuthorityDataPayload> result = store.find(priorAuthorityId);

    assertThat(result).contains(expectedPayload);
  }

  @Test
  void givenNoDraft_whenFind_thenReturnsEmpty() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.empty());

    Optional<PriorAuthorityDataPayload> result = store.find(priorAuthorityId);

    assertThat(result).isEmpty();
  }

  @Test
  void whenDelete_thenDelegatesToRepositoryDeleteById() {
    UUID priorAuthorityId = UUID.randomUUID();

    store.delete(priorAuthorityId);

    verify(repository).deleteById(eq(priorAuthorityId));
  }

  @Test
  void
      givenDraftWithNullDocuments_whenAppendDocument_thenAddsFirstDocumentAndPreservesOtherFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant occurredAt = Instant.parse("2026-08-02T11:00:00Z");
    PriorAuthorityContent content =
        new PriorAuthorityContent("EXPERT", "reason", null, null, null, null);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(priorAuthorityId, applicationId, content, "req", createdAt);
    PriorAuthorityDraft existing =
        PriorAuthorityDraft.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .payload(payload)
            .payloadHash("existing-hash")
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(existing));
    PriorAuthorityDocument document = new PriorAuthorityDocument(documentId, "evidence.pdf");

    store.appendDocument(priorAuthorityId, document, occurredAt);

    ArgumentCaptor<PriorAuthorityDraft> captor = ArgumentCaptor.forClass(PriorAuthorityDraft.class);
    verify(repository).saveAndFlush(captor.capture());
    PriorAuthorityDraft saved = captor.getValue();
    assertThat(saved.getPriorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(saved.getApplicationId()).isEqualTo(applicationId);
    assertThat(saved.getPayloadHash()).isEqualTo("existing-hash");
    assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    assertThat(saved.getUpdatedAt()).isEqualTo(occurredAt);
    assertThat(saved.getPayload().content().documents()).containsExactly(document);
    assertThat(saved.getPayload().content().priorAuthorityType()).isEqualTo("EXPERT");
    assertThat(saved.getPayload().content().justification()).isEqualTo("reason");
  }

  @Test
  void givenDraftWithExistingDocuments_whenAppendDocument_thenAppendsWithoutRemovingExisting() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityDocument firstDocument =
        new PriorAuthorityDocument(UUID.randomUUID(), "first.pdf");
    PriorAuthorityContent content =
        new PriorAuthorityContent("EXPERT", null, null, null, null, List.of(firstDocument));
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, "req", Instant.now());
    PriorAuthorityDraft existing =
        PriorAuthorityDraft.builder().priorAuthorityId(priorAuthorityId).payload(payload).build();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.of(existing));
    PriorAuthorityDocument secondDocument =
        new PriorAuthorityDocument(UUID.randomUUID(), "second.pdf");

    store.appendDocument(priorAuthorityId, secondDocument, Instant.now());

    ArgumentCaptor<PriorAuthorityDraft> captor = ArgumentCaptor.forClass(PriorAuthorityDraft.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getPayload().content().documents())
        .containsExactly(firstDocument, secondDocument);
  }

  @Test
  void givenNoDraft_whenAppendDocument_thenThrowsIllegalStateException() {
    UUID priorAuthorityId = UUID.randomUUID();
    when(repository.findById(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                store.appendDocument(
                    priorAuthorityId,
                    new PriorAuthorityDocument(UUID.randomUUID(), "evidence.pdf"),
                    Instant.now()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No draft found for submission: " + priorAuthorityId);
  }
}
