package uk.gov.justice.laa.dstew.access.command.application.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationSearchView;
import uk.gov.justice.laa.dstew.access.query.application.search.ApplicationSearchRepository;
import uk.gov.justice.laa.dstew.access.query.application.search.ApplicationSearchViewHydrator;

/** Writes and retrieves immutable application-data versions. */
@Component
public class ApplicationDataStore {

  private final ApplicationDataRepository repository;
  private final ApplicationSearchRepository searchViewRepository;
  private final ApplicationSearchViewHydrator searchViewHydrator;

  public ApplicationDataStore(
      ApplicationDataRepository repository,
      ApplicationSearchRepository searchViewRepository,
      ApplicationSearchViewHydrator searchViewHydrator) {
    this.repository = repository;
    this.searchViewRepository = searchViewRepository;
    this.searchViewHydrator = searchViewHydrator;
  }

  /**
   * Appends an immutable version of an application's sensitive data.
   *
   * @param applicationId the application identifier
   * @param version the data version
   * @param details the application details to persist
   * @return the fingerprint of the serialised request
   */
  public String append(UUID applicationId, long version, ApplicationCreationDetails details) {
    String fingerprint = fingerprint(details.serialisedRequest());
    ApplicationData applicationData = ApplicationData.builder()
        .id(new ApplicationDataId(applicationId, version))
        .payload(ApplicationDataPayload.from(details))
        .payloadHash(fingerprint)
        .createdAt(details.occurredAt())
        .build();
    ApplicationSearchView searchView = ApplicationSearchView.builder().applicationId(applicationId).build();
    searchViewHydrator.hydrate(searchView, applicationData.getPayload());
    repository.saveAndFlush(applicationData);
    searchViewRepository.saveAndFlush(searchView);
    return fingerprint;
  }

  /**
   * Appends an already reconstructed application-data payload as a new immutable version.
   *
   * @param applicationId the application identifier
   * @param version the new data version
   * @param payload the complete payload for that version
   * @param serialisedRequest the request responsible for the version
   * @param occurredAt when the version was created
   * @return the fingerprint of the request
   */
  public String append(
      UUID applicationId,
      long version,
      ApplicationDataPayload payload,
      String serialisedRequest,
      java.time.Instant occurredAt) {
    String fingerprint = fingerprint(serialisedRequest);
    ApplicationData applicationData = ApplicationData.builder()
        .id(new ApplicationDataId(applicationId, version))
        .payload(payload)
        .payloadHash(fingerprint)
        .createdAt(occurredAt)
        .build();
    ApplicationSearchView searchView = ApplicationSearchView.builder().applicationId(applicationId).build();
    searchViewHydrator.hydrate(searchView, payload);
    repository.saveAndFlush(applicationData);
    searchViewRepository.saveAndFlush(searchView);
    return fingerprint;
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
    return repository
        .findById(new ApplicationDataId(applicationId, version))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Application data not found for " + applicationId + " version " + version))
        .getPayload();
  }

  /**
   * Retrieves the available application-data versions for the supplied identifiers.
   *
   * @param ids the application-data identifiers to retrieve
   * @return payloads keyed by application-data identifier
   */
  public Map<ApplicationDataId, ApplicationDataPayload> getAll(Collection<ApplicationDataId> ids) {
    return repository.findAllById(ids).stream()
        .collect(Collectors.toMap(ApplicationData::getId, ApplicationData::getPayload));
  }

  /**
   * Calculates a stable SHA-256 fingerprint for a serialised request.
   *
   * @param value the serialised request
   * @return the lowercase hexadecimal SHA-256 digest
   */
  public static String fingerprint(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
