package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

/** Stores metadata independently of the prior-authority draft payload. */
@Component
public class UploadedDocumentStore {

  private final UploadedDocumentRepository repository;

  public UploadedDocumentStore(UploadedDocumentRepository repository) {
    this.repository = repository;
  }

  /** Persists the lightweight filename mapping for a newly uploaded prior-authority document. */
  public void save(UUID submissionId, PriorAuthorityDocument document) {
    repository.saveAndFlush(
        UploadedDocument.builder()
            .documentId(document.documentId())
            .submissionId(submissionId)
            .originalFilename(
                Objects.requireNonNull(document.fileName(), "originalFilename must not be null"))
            .build());
  }

  /** Returns the stored row for the requested document id, if present. */
  public Optional<UploadedDocument> findById(UUID documentId) {
    return repository.findById(documentId);
  }

  /** Validates that the identified document is still linked to the supplied submission. */
  public void updateDocumentType(UUID submissionId, UUID documentId, String documentType) {
    repository
        .findById(documentId)
        .filter(existing -> existing.getSubmissionId().equals(submissionId))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Document %s not found for Prior Authority %s"
                        .formatted(documentId, submissionId)));
  }

  /**
   * Validates ownership for the identified document; its filename mapping is retained for replay.
   */
  public void delete(UUID submissionId, UUID documentId) {
    repository
        .findById(documentId)
        .filter(existing -> existing.getSubmissionId().equals(submissionId))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Document %s not found for Prior Authority %s"
                        .formatted(documentId, submissionId)));
  }

  /** Returns stored filename mappings in the same order as the requested document identifiers. */
  public List<UploadedDocument> findAllInOrder(Collection<UUID> documentIds) {
    Map<UUID, UploadedDocument> documentsById =
        repository.findAllById(documentIds).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    UploadedDocument::getDocumentId, Function.identity()));
    return documentIds.stream().map(documentsById::get).filter(java.util.Objects::nonNull).toList();
  }
}
