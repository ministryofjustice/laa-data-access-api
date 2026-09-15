package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Writes and retrieves immutable prior-authority data versions. */
@Component
public class PriorAuthorityDataStore {

  private final PriorAuthorityDataRepository repository;

  public PriorAuthorityDataStore(PriorAuthorityDataRepository repository) {
    this.repository = repository;
  }

  /**
   * Appends an immutable version of a prior-authority submission's sensitive data.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @param dataVersion the data version
   * @param applicationId the parent application identifier
   * @param payload the payload to persist
   * @param serialisedRequest the raw request responsible for this version
   * @param occurredAt when the version was created
   * @return the fingerprint of the serialised request
   */
  public String append(
      UUID priorAuthorityId,
      long dataVersion,
      UUID applicationId,
      PriorAuthorityDataPayload payload,
      String serialisedRequest,
      Instant occurredAt) {
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    repository.saveAndFlush(
        PriorAuthorityData.builder()
            .id(new PriorAuthorityDataId(priorAuthorityId, dataVersion))
            .applicationId(applicationId)
            .payload(payload)
            .payloadHash(fingerprint)
            .createdAt(occurredAt)
            .build());
    return fingerprint;
  }

  /**
   * Retrieves a specific version of a prior-authority submission's sensitive data.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @param dataVersion the data version
   * @return the stored prior-authority data payload
   * @throws IllegalStateException when the referenced version does not exist
   */
  public PriorAuthorityDataPayload get(UUID priorAuthorityId, long dataVersion) {
    return repository
        .findById(new PriorAuthorityDataId(priorAuthorityId, dataVersion))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Prior authority data not found for submission "
                        + priorAuthorityId
                        + " version "
                        + dataVersion))
        .getPayload();
  }
}
