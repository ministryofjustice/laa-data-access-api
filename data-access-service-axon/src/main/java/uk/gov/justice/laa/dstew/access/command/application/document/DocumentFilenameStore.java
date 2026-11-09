package uk.gov.justice.laa.dstew.access.command.application.document;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Store for document filenames. Bulk hydration uses document IDs selected from event-derived owner
 * state; individual lookups and mutations also check ownership.
 */
@Component
@RequiredArgsConstructor
public class DocumentFilenameStore {

  private final DocumentFilenameRepository repository;

  /** Records a filename for a newly uploaded document. */
  public void record(UUID ownerId, UUID documentId, String filename, Instant createdAt) {
    repository.save(
        DocumentFilename.builder()
            .documentId(documentId)
            .ownerId(ownerId)
            .filename(filename)
            .createdAt(createdAt)
            .build());
  }

  /** Marks a document's filename as deleted, keeping the row for later lookup. */
  public void markDeleted(UUID ownerId, UUID documentId, Instant deletedAt) {
    repository
        .findByOwnerIdAndDocumentId(ownerId, documentId)
        .ifPresent(filename -> filename.markDeleted(deletedAt));
  }

  /** Returns filenames for document IDs selected from event-derived document state. */
  public Map<UUID, String> findAllByIds(Collection<UUID> documentIds) {
    if (documentIds.isEmpty()) {
      return Map.of();
    }
    return repository.findAllById(documentIds).stream()
        .collect(Collectors.toMap(DocumentFilename::getDocumentId, DocumentFilename::getFilename));
  }

  /** Returns the filename for a document, including one that has been deleted. */
  public Optional<String> findIncludingDeleted(UUID ownerId, UUID documentId) {
    return repository
        .findByOwnerIdAndDocumentId(ownerId, documentId)
        .map(DocumentFilename::getFilename);
  }
}
