package uk.gov.justice.laa.dstew.access.command.application.priorauthority.data;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.JacksonExceptionMessageBuilder;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Writes and retrieves immutable prior-authority data versions. */
@Component
public class PriorAuthorityDataStore {

  private final PriorAuthorityDataRepository repository;
  private final ErrorReporter errorReporter;

  /**
   * Convenience constructor retained for existing call sites/tests that do not need to verify
   * Sentry reporting explicitly.
   */
  public PriorAuthorityDataStore(PriorAuthorityDataRepository repository) {
    this(repository, new ErrorReporter());
  }

  /** Creates the store with its repository and error-reporting dependencies. */
  @Autowired
  public PriorAuthorityDataStore(
      PriorAuthorityDataRepository repository, ErrorReporter errorReporter) {
    this.repository = repository;
    this.errorReporter = errorReporter;
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
   * Finds a specific version of a prior-authority submission's data, if still retained.
   *
   * @param priorAuthorityId the prior-authority submission identifier
   * @param dataVersion the data version
   * @return the stored payload, or empty if that version is not present
   */
  public Optional<PriorAuthorityDataPayload> findPayload(UUID priorAuthorityId, long dataVersion) {
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, dataVersion);
    return retrieve(id, () -> repository.findById(id).map(PriorAuthorityData::getPayload));
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
    PriorAuthorityDataId id = new PriorAuthorityDataId(priorAuthorityId, dataVersion);
    return retrieve(
        id,
        () ->
            repository
                .findById(id)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Prior authority data not found for submission "
                                + priorAuthorityId
                                + " version "
                                + dataVersion))
                .getPayload());
  }

  /** Returns whether immutable submitted data already exists for this prior-authority ID. */
  public boolean exists(UUID priorAuthorityId) {
    return repository.existsById(new PriorAuthorityDataId(priorAuthorityId, 0L));
  }

  private <T> T retrieve(PriorAuthorityDataId id, Supplier<T> operation) {
    try {
      return operation.get();
    } catch (RuntimeException exception) {
      JacksonExceptionMessageBuilder.findJacksonException(exception)
          .ifPresentOrElse(
              jacksonException ->
                  errorReporter.report(
                      "prior_authority_data JSON deserialization failed: priorAuthorityDataId=%s, "
                              .formatted(id)
                          + "jacksonExceptionType=%s, jacksonMessage=%s"
                              .formatted(
                                  jacksonException.getClass().getSimpleName(),
                                  JacksonExceptionMessageBuilder.buildMessage(jacksonException)),
                      exception),
              () ->
                  errorReporter.report(
                      "prior_authority_data load failed: priorAuthorityDataId=%s".formatted(id),
                      exception));
      throw exception;
    }
  }
}
