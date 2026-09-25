package uk.gov.justice.laa.dstew.access.command.application.data;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Writes and retrieves the mutable draft content for an in-progress Application. */
@Component
public class ApplicationDraftStore {

  private final ApplicationDraftRepository repository;

  public ApplicationDraftStore(ApplicationDraftRepository repository) {
    this.repository = repository;
  }

  /**
   * Inserts or updates the draft row for the given application.
   *
   * @param applicationId the application identifier
   * @param payload the payload to persist
   * @param serialisedRequest the raw request responsible for this update
   * @param occurredAt when the update occurred
   * @return the fingerprint of the serialised request
   */
  public String upsert(
      UUID applicationId,
      ApplicationDraftPayload payload,
      String serialisedRequest,
      Instant occurredAt) {
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    Instant createdAt =
        repository.findById(applicationId).map(ApplicationDraft::getCreatedAt).orElse(occurredAt);
    repository.saveAndFlush(
        ApplicationDraft.builder()
            .applicationId(applicationId)
            .payload(payload)
            .payloadHash(fingerprint)
            .createdAt(createdAt)
            .updatedAt(occurredAt)
            .build());
    return fingerprint;
  }

  /**
   * Retrieves the current draft content for an application, if one exists.
   *
   * @param applicationId the application identifier
   * @return the stored draft payload, or {@link Optional#empty()} when no draft exists
   */
  public Optional<ApplicationDraftPayload> find(UUID applicationId) {
    return repository.findById(applicationId).map(ApplicationDraft::getPayload);
  }

  /**
   * Deletes the draft row for an application, typically once it has been submitted.
   *
   * @param applicationId the application identifier
   */
  public void delete(UUID applicationId) {
    repository.deleteById(applicationId);
  }
}
