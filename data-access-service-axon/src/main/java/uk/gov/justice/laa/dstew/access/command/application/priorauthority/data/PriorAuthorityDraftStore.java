package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
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
   * @param submissionId the prior-authority submission identifier
   * @param applicationId the parent application identifier
   * @param payload the payload to persist
   * @param serialisedRequest the raw request responsible for this update
   * @param occurredAt when the update occurred
   * @return the fingerprint of the serialised request
   */
  public String upsert(
      UUID submissionId,
      UUID applicationId,
      PriorAuthorityDataPayload payload,
      String serialisedRequest,
      Instant occurredAt) {
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    Instant createdAt =
        repository.findById(submissionId).map(PriorAuthorityDraft::getCreatedAt).orElse(occurredAt);
    repository.saveAndFlush(
        PriorAuthorityDraft.builder()
            .submissionId(submissionId)
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
   * @param submissionId the prior-authority submission identifier
   * @return the stored draft payload
   * @throws IllegalStateException when no draft exists for the given submission
   */
  public PriorAuthorityDataPayload get(UUID submissionId) {
    return repository
        .findById(submissionId)
        .map(PriorAuthorityDraft::getPayload)
        .orElseThrow(
            () -> new IllegalStateException("No draft found for submission: " + submissionId));
  }

  /**
   * Retrieves the current draft content for a prior-authority submission, if one exists.
   *
   * @param submissionId the prior-authority submission identifier
   * @return the stored draft payload, or {@link Optional#empty()} when no draft exists
   */
  public Optional<PriorAuthorityDataPayload> find(UUID submissionId) {
    return repository.findById(submissionId).map(PriorAuthorityDraft::getPayload);
  }

  /**
   * Deletes the draft row for a prior-authority submission, typically once it has been submitted.
   *
   * @param submissionId the prior-authority submission identifier
   */
  public void delete(UUID submissionId) {
    repository.deleteById(submissionId);
  }
}
