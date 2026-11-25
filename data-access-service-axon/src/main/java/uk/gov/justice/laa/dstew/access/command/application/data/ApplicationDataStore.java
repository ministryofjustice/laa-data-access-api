package uk.gov.justice.laa.dstew.access.command.application.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.applicationcontent.JacksonExceptionMessageBuilder;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Writes and retrieves immutable application-data versions. */
@Component
public class ApplicationDataStore {

  private final ApplicationDataRepository repository;
  private final ErrorReporter errorReporter;

  public ApplicationDataStore(ApplicationDataRepository repository, ErrorReporter errorReporter) {
    this.repository = repository;
    this.errorReporter = errorReporter;
  }

  /**
   * Appends an immutable version of an application's sensitive data.
   *
   * @param applicationId the application identifier
   * @param version the data version
   * @param details the application details to persist
   */
  public void append(UUID applicationId, long version, ApplicationCreationDetails details) {
    repository.saveAndFlush(
        ApplicationData.builder()
            .id(new ApplicationDataId(applicationId, version))
            .payload(ApplicationDataPayload.from(details))
            .payloadHash(PayloadFingerprint.compute(details.serialisedRequest()))
            .createdAt(details.occurredAt())
            .build());
  }

  /**
   * Appends an already reconstructed application-data payload as a new immutable version.
   *
   * @param applicationId the application identifier
   * @param version the new data version
   * @param payload the complete payload for that version
   * @param serialisedRequest the request responsible for the version
   * @param occurredAt when the version was created
   */
  public void append(
      UUID applicationId,
      long version,
      ApplicationDataPayload payload,
      String serialisedRequest,
      java.time.Instant occurredAt) {
    repository.saveAndFlush(
        ApplicationData.builder()
            .id(new ApplicationDataId(applicationId, version))
            .payload(payload)
            .payloadHash(PayloadFingerprint.compute(serialisedRequest))
            .createdAt(occurredAt)
            .build());
  }

  /**
   * Retrieves a specific version of an application's sensitive data.
   *
   * @param applicationId the application identifier
   * @param version the data version
   * @return the stored application-data payload
   * @throws IllegalStateException when the referenced version does not exist
   */
  public ApplicationDataPayload get(UUID applicationId, long version) {
    ApplicationDataId id = new ApplicationDataId(applicationId, version);
    return retrieve(
        "applicationDataIds",
        List.of(id),
        () ->
            repository
                .findById(id)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Application data not found for "
                                + applicationId
                                + " version "
                                + version))
                .getPayload());
  }

  /**
   * Finds a specific version of an application's data, if it is still retained.
   *
   * @param applicationId the application identifier
   * @param version the data version
   * @return the stored payload, or empty if that version is not present
   */
  public Optional<ApplicationDataPayload> findPayload(UUID applicationId, long version) {
    ApplicationDataId id = new ApplicationDataId(applicationId, version);
    return retrieve(
        "applicationDataIds",
        List.of(id),
        () -> repository.findById(id).map(ApplicationData::getPayload));
  }

  /**
   * Returns the latest immutable data version currently stored for an application.
   *
   * <p>Intended for command-side use only, to capture a stable version pointer into an event at
   * write time. Projections must not call this at replay time: they hydrate from the version pinned
   * in the event they are handling, so that a rebuild reproduces the original result.
   *
   * @param applicationId the application identifier
   * @return the highest stored data version for the application
   * @throws IllegalStateException when the application has no stored data
   */
  public long latestVersion(UUID applicationId) {
    return repository
        .findFirstByIdApplicationIdOrderByIdVersionDesc(applicationId)
        .orElseThrow(
            () -> new IllegalStateException("Application data not found for " + applicationId))
        .getId()
        .version();
  }

  /**
   * Retrieves the available application-data versions for the supplied identifiers.
   *
   * @param ids the application-data identifiers to retrieve
   * @return payloads keyed by application-data identifier
   */
  public Map<ApplicationDataId, ApplicationDataPayload> getAll(Collection<ApplicationDataId> ids) {
    return retrieve(
        "applicationDataIds",
        ids,
        () ->
            repository.findAllById(ids).stream()
                .collect(Collectors.toMap(ApplicationData::getId, ApplicationData::getPayload)));
  }

  private <T> T retrieve(String identifierName, Object identifiers, Supplier<T> operation) {
    try {
      return operation.get();
    } catch (RuntimeException exception) {
      JacksonExceptionMessageBuilder.findJacksonException(exception)
          .ifPresentOrElse(
              jacksonException ->
                  errorReporter.report(
                      "application_data JSON deserialization failed: %s=%s, "
                              .formatted(identifierName, identifiers)
                          + "jacksonExceptionType=%s, jacksonMessage=%s"
                              .formatted(
                                  jacksonException.getClass().getSimpleName(),
                                  JacksonExceptionMessageBuilder.buildMessage(jacksonException)),
                      exception),
              () ->
                  errorReporter.report(
                      "application_data load failed: %s=%s".formatted(identifierName, identifiers),
                      exception));
      throw exception;
    }
  }
}
