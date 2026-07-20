package uk.gov.justice.laa.dstew.access.command.application.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationPiiRepository.ExistingFragment;

/** Writes and retrieves immutable application-data versions. */
@Component
@Transactional
public class ApplicationDataStore {

  /**
   * Keys within the applicationContent map that contain PII and must be externalised. The special
   * key {@code "individuals"} is synthetic — the individuals list is injected into the map before
   * persist and extracted again on hydration.
   */
  static final Set<String> PII_SECTION_KEYS =
      Set.of(
          "applicant",
          "applicationMerits",
          "individuals",
          "partner",
          "opponentDetails",
          "childrenDetails",
          "meansAssessment",
          "capitalDetails",
          "incomeDetails");

  private static final String PII_REF_PREFIX = "pii:";

  private final ApplicationDataRepository repository;
  private final ApplicationPiiRepository piiRepository;
  private final ObjectMapper objectMapper;

  public ApplicationDataStore(
      ApplicationDataRepository repository,
      ApplicationPiiRepository piiRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.piiRepository = piiRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Appends an immutable version of an application's sensitive data. Converts {@code
   * ApplicationContent} to a raw map, externalises PII sections (and the individuals list) as
   * fragment refs stored inline as {@code "pii:<uuid>"} strings, then persists the thin map.
   *
   * @param applicationId the application identifier
   * @param version the data version
   * @param details the application details to persist
   * @return the fingerprint of the serialised request
   */
  public String append(UUID applicationId, long version, ApplicationCreationDetails details) {
    String fingerprint = fingerprint(details.serialisedRequest());
    Map<String, Object> contentMap = toMutableMap(details.applicationContent());
    if (!details.individuals().isEmpty()) {
      contentMap.put("individuals", details.individuals());
    }
    externalisePiiInMap(applicationId, contentMap, details.occurredAt());
    repository.saveAndFlush(
        ApplicationData.builder()
            .id(new ApplicationDataId(applicationId, version))
            .payload(
                new ApplicationDataPayload(
                    details.laaReference(),
                    contentMap,
                    List.of(),
                    details.applyApplicationId(),
                    details.submittedAt(),
                    details.officeCode(),
                    details.usedDelegatedFunctions(),
                    details.categoryOfLaw(),
                    details.matterType(),
                    details.proceedings(),
                    null,
                    null,
                    null,
                    Map.of(),
                    null,
                    null,
                    null,
                    null))
            .payloadHash(fingerprint)
            .createdAt(details.occurredAt())
            .piiStatus(PiiStatus.PRESENT)
            .build());
    return fingerprint;
  }

  /**
   * Appends an already-thin application-data payload (whose {@code applicationContent} map already
   * contains inline {@code "pii:<uuid>"} references) as a new immutable version. No PII
   * externalisation is performed.
   *
   * @param applicationId the application identifier
   * @param version the new data version
   * @param payload the thin payload for that version (PII refs already embedded)
   * @param serialisedRequest the request responsible for the version
   * @param occurredAt when the version was created
   * @return the fingerprint of the request
   */
  public String append(
      UUID applicationId,
      long version,
      ApplicationDataPayload payload,
      String serialisedRequest,
      Instant occurredAt) {
    String fingerprint = fingerprint(serialisedRequest);
    repository.saveAndFlush(
        ApplicationData.builder()
            .id(new ApplicationDataId(applicationId, version))
            .payload(payload)
            .payloadHash(fingerprint)
            .createdAt(occurredAt)
            .piiStatus(PiiStatus.PRESENT)
            .build());
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
   * Hydrates a thin payload by resolving {@code "pii:<uuid>"} strings in the applicationContent map
   * back to their original objects. The synthetic {@code "individuals"} key is extracted and placed
   * in the returned payload's {@code individuals} field.
   *
   * @param thinPayload the stored thin payload containing inline pii refs
   * @return the fully hydrated payload, or the original payload unchanged when no refs are found
   */
  public ApplicationDataPayload hydrate(ApplicationDataPayload thinPayload) {
    Map<String, Object> contentMap = thinPayload.applicationContent();
    if (contentMap == null || contentMap.isEmpty()) {
      return thinPayload;
    }

    Map<String, UUID> piiRefsByKey = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : contentMap.entrySet()) {
      if (entry.getValue() instanceof String s && s.startsWith(PII_REF_PREFIX)) {
        piiRefsByKey.put(entry.getKey(), UUID.fromString(s.substring(PII_REF_PREFIX.length())));
      }
    }
    if (piiRefsByKey.isEmpty()) {
      return thinPayload;
    }

    Map<UUID, byte[]> fragments =
        piiRepository.findFragments(new HashSet<>(piiRefsByKey.values()));
    Map<String, Object> hydratedMap = new LinkedHashMap<>(contentMap);
    List<ApplicationIndividual> individuals = thinPayload.individuals();

    for (Map.Entry<String, UUID> entry : piiRefsByKey.entrySet()) {
      String key = entry.getKey();
      byte[] bytes = fragments.get(entry.getValue());
      if (bytes == null) continue;
      if ("individuals".equals(key)) {
        individuals = deserialise(bytes, new TypeReference<List<ApplicationIndividual>>() {});
        hydratedMap.remove(key);
      } else {
        hydratedMap.put(key, deserialise(bytes, Object.class));
      }
    }

    return new ApplicationDataPayload(
        thinPayload.laaReference(),
        hydratedMap,
        individuals,
        thinPayload.applyApplicationId(),
        thinPayload.submittedAt(),
        thinPayload.officeCode(),
        thinPayload.usedDelegatedFunctions(),
        thinPayload.categoryOfLaw(),
        thinPayload.matterType(),
        thinPayload.proceedings(),
        thinPayload.serialisedRequest(),
        thinPayload.overallDecision(),
        thinPayload.autoGranted(),
        thinPayload.meritsDecisions(),
        thinPayload.certificate(),
        thinPayload.decisionSerialisedRequest(),
        thinPayload.decisionEventDescription(),
        thinPayload.assignmentEventDescription());
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

  /**
   * Replaces PII sections in the map with {@code "pii:<uuid>"} strings in-place. Existing pii ref
   * strings are left unchanged (idempotent).
   */
  private void externalisePiiInMap(
      UUID applicationId, Map<String, Object> map, Instant occurredAt) {
    for (String key : PII_SECTION_KEYS) {
      Object value = map.get(key);
      if (value == null || (value instanceof String s && s.startsWith(PII_REF_PREFIX))) continue;
      UUID ref = externaliseFragment(applicationId, key, value, occurredAt);
      map.put(key, PII_REF_PREFIX + ref);
    }
  }

  private UUID externaliseFragment(
      UUID applicationId, String sectionName, Object content, Instant occurredAt) {
    byte[] bytes = serialise(content);
    String hash = fingerprintBytes(bytes);
    Optional<ExistingFragment> existing =
        piiRepository.findLatestFragment(applicationId, sectionName);
    if (existing.isPresent() && existing.get().contentHash().equals(hash)) {
      return existing.get().ref();
    }
    UUID newRef = UUID.randomUUID();
    piiRepository.saveFragment(applicationId, newRef, sectionName, bytes, occurredAt);
    return newRef;
  }

  private Map<String, Object> toMutableMap(Object value) {
    if (value == null) return new LinkedHashMap<>();
    return new LinkedHashMap<>(
        objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {}));
  }

  private byte[] serialise(Object content) {
    try {
      return objectMapper.writeValueAsBytes(content);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialise PII fragment", exception);
    }
  }

  private <T> T deserialise(byte[] bytes, Class<T> type) {
    try {
      return objectMapper.readValue(bytes, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to deserialise PII fragment", exception);
    }
  }

  private <T> T deserialise(byte[] bytes, TypeReference<T> typeRef) {
    try {
      return objectMapper.readValue(bytes, typeRef);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to deserialise PII fragment", exception);
    }
  }

  private static String fingerprintBytes(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
