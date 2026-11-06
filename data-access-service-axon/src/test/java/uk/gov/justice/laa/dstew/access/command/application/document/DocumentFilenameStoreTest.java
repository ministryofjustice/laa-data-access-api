package uk.gov.justice.laa.dstew.access.command.application.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentFilenameStoreTest {

  private static final Instant CREATED_AT = Instant.parse("2026-09-10T09:00:00Z");
  private static final Instant DELETED_AT = Instant.parse("2026-09-11T10:00:00Z");

  @Mock private DocumentFilenameRepository repository;
  @InjectMocks private DocumentFilenameStore store;

  @Test
  void givenNewDocument_whenRecorded_thenSavesNewActiveRow() {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();

    store.record(ownerId, documentId, "evidence.PDF", CREATED_AT);

    ArgumentCaptor<DocumentFilename> saved = ArgumentCaptor.forClass(DocumentFilename.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue())
        .extracting(
            DocumentFilename::getDocumentId,
            DocumentFilename::getOwnerId,
            DocumentFilename::getFilename,
            DocumentFilename::getCreatedAt,
            DocumentFilename::getDeletedAt)
        .containsExactly(documentId, ownerId, "evidence.PDF", CREATED_AT, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void givenDocumentIds_whenFound_thenMapsRowsRegardlessOfDeletionFlag(boolean deleted) {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    List<UUID> documentIds = List.of(documentId, UUID.randomUUID());
    when(repository.findAllById(documentIds))
        .thenReturn(List.of(row(ownerId, documentId, "evidence.PDF", deleted ? DELETED_AT : null)));

    assertThat(store.findAllByIds(documentIds)).containsExactly(entry(documentId, "evidence.PDF"));

    verify(repository).findAllById(documentIds);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void givenMissingDocumentIds_whenFound_thenReturnsEmptyMap() {
    List<UUID> documentIds = List.of(UUID.randomUUID());
    when(repository.findAllById(documentIds)).thenReturn(List.of());

    assertThat(store.findAllByIds(documentIds)).isEmpty();
  }

  @Test
  void givenNoDocumentIds_whenFound_thenDoesNotQueryRepository() {
    assertThat(store.findAllByIds(List.of())).isEmpty();

    verifyNoInteractions(repository);
  }

  @Test
  void givenDeletedDocument_whenFindIncludingDeleted_thenReturnsFilename() {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(repository.findByOwnerIdAndDocumentId(ownerId, documentId))
        .thenReturn(Optional.of(row(ownerId, documentId, "evidence.PDF", DELETED_AT)));

    assertThat(store.findIncludingDeleted(ownerId, documentId)).contains("evidence.PDF");
  }

  @Test
  void givenDocumentOwnedByAnotherOwner_whenFindIncludingDeleted_thenReturnsEmpty() {
    UUID otherOwnerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(repository.findByOwnerIdAndDocumentId(otherOwnerId, documentId))
        .thenReturn(Optional.empty());

    assertThat(store.findIncludingDeleted(otherOwnerId, documentId)).isEmpty();

    verify(repository).findByOwnerIdAndDocumentId(otherOwnerId, documentId);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void givenActiveDocument_whenMarkedDeleted_thenSetsDeletedAt() {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    DocumentFilename row = row(ownerId, documentId, "evidence.PDF", null);
    when(repository.findByOwnerIdAndDocumentId(ownerId, documentId)).thenReturn(Optional.of(row));

    store.markDeleted(ownerId, documentId, DELETED_AT);

    assertThat(row.getDeletedAt()).isEqualTo(DELETED_AT);
    assertThat(row.getFilename()).isEqualTo("evidence.PDF");
  }

  @Test
  void givenMissingDocument_whenMarkedDeleted_thenIsNoOp() {
    UUID ownerId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    when(repository.findByOwnerIdAndDocumentId(ownerId, documentId)).thenReturn(Optional.empty());

    assertThatNoException().isThrownBy(() -> store.markDeleted(ownerId, documentId, DELETED_AT));

    verify(repository).findByOwnerIdAndDocumentId(ownerId, documentId);
    verifyNoMoreInteractions(repository);
  }

  private static DocumentFilename row(
      UUID ownerId, UUID documentId, String filename, Instant deletedAt) {
    return DocumentFilename.builder()
        .ownerId(ownerId)
        .documentId(documentId)
        .filename(filename)
        .createdAt(CREATED_AT)
        .deletedAt(deletedAt)
        .build();
  }
}
