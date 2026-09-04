package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Writes and retrieves the mutable draft content for a prior-authority submission. */
@Component
public class PriorAuthorityDraftStore {

  private final PriorAuthorityDraftRepository repository;

  public PriorAuthorityDraftStore(PriorAuthorityDraftRepository repository) {
    this.repository = repository;
  }

  /**
   * Inserts or updates the draft row for the given submission.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @param applicationId the parent application identifier
   * @param payload the payload to persist
   * @param serialisedRequest the raw request responsible for this update
   * @param occurredAt when the update occurred
   * @return the fingerprint of the serialised request
   */
  public String upsert(
      UUID priorAuthorityId,
      UUID applicationId,
      PriorAuthorityDataPayload payload,
      String serialisedRequest,
      Instant occurredAt) {
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    Instant createdAt =
        repository
            .findById(priorAuthorityId)
            .map(PriorAuthorityDraft::getCreatedAt)
            .orElse(occurredAt);
    repository.saveAndFlush(
        PriorAuthorityDraft.builder()
            .priorAuthorityId(priorAuthorityId)
            .applicationId(applicationId)
            .payload(payload)
            .payloadHash(fingerprint)
            .createdAt(createdAt)
            .updatedAt(occurredAt)
            .build());
    return fingerprint;
  }

  /**
   * Retrieves the current draft content for a prior-authority submission.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @return the stored draft payload
   * @throws IllegalStateException when no draft exists for the given submission
   */
  public PriorAuthorityDataPayload get(UUID priorAuthorityId) {
    return repository
        .findById(priorAuthorityId)
        .map(PriorAuthorityDraft::getPayload)
        .orElseThrow(
            () -> new IllegalStateException("No draft found for submission: " + priorAuthorityId));
  }

  /**
   * Retrieves the current draft content for a prior-authority submission, if one exists.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @return the stored draft payload, or {@link Optional#empty()} when no draft exists
   */
  public Optional<PriorAuthorityDataPayload> find(UUID priorAuthorityId) {
    return repository.findById(priorAuthorityId).map(PriorAuthorityDraft::getPayload);
  }

  /**
   * Deletes the draft row for a prior-authority submission, typically once it has been submitted.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   */
  public void delete(UUID priorAuthorityId) {
    repository.deleteById(priorAuthorityId);
  }

  /**
   * Appends a document to a draft's content, preserving all other content fields and the existing
   * payload hash and created timestamp.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @param document the document to append
   * @param occurredAt when the attachment occurred
   * @throws IllegalStateException when no draft exists for the given submission
   */
  public void appendDocument(
      UUID priorAuthorityId, PriorAuthorityDocument document, Instant occurredAt) {
    PriorAuthorityDraft draft =
        repository
            .findById(priorAuthorityId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No draft found for submission: " + priorAuthorityId));
    PriorAuthorityDataPayload payload = draft.getPayload();
    PriorAuthorityContent content = payload.content();
    List<PriorAuthorityDocument> documents =
        content.documents() == null ? new ArrayList<>() : new ArrayList<>(content.documents());
    documents.add(document);
    PriorAuthorityContent updatedContent =
        new PriorAuthorityContent(
            content.priorAuthorityType(),
            content.justification(),
            content.expertDetails(),
            content.counselDetails(),
            content.disbursementDetails(),
            documents);
    PriorAuthorityDataPayload updatedPayload =
        new PriorAuthorityDataPayload(
            payload.priorAuthorityId(),
            payload.applicationId(),
            updatedContent,
            payload.serialisedRequest(),
            payload.submittedAt());
    repository.saveAndFlush(
        PriorAuthorityDraft.builder()
            .priorAuthorityId(draft.getPriorAuthorityId())
            .applicationId(draft.getApplicationId())
            .payload(updatedPayload)
            .payloadHash(draft.getPayloadHash())
            .createdAt(draft.getCreatedAt())
            .updatedAt(occurredAt)
            .build());
  }
}
