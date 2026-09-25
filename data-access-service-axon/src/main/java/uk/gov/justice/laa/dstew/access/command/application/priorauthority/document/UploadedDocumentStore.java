package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stores original filenames separately from event-sourced document facts. */
@Component
public class UploadedDocumentStore {

  private final UploadedDocumentRepository repository;

  public UploadedDocumentStore(UploadedDocumentRepository repository) {
    this.repository = repository;
  }

  /** Blindly upserts the lightweight filename mapping for an uploaded document. */
  public void save(UUID submissionId, UUID documentId, String originalFilename) {
    repository.upsert(
        documentId,
        submissionId,
        Objects.requireNonNull(originalFilename, "originalFilename must not be null"));
  }

  /** Returns filename mappings for the requested document identifiers. */
  public List<UploadedDocument> findAllById(Iterable<UUID> documentIds) {
    return repository.findAllById(documentIds);
  }
}
