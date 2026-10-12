package uk.gov.justice.laa.dstew.access.command.application.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;

class ApplicationDataStoreTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"substantiveLevelOfService\":3,\"substantiveLevelOfServiceCode\":4,\"emergencyLevelOfService\":2,\"emergencyLevelOfServiceCode\":9}",
        "{\"emergencyLevelOfServiceCode\":9,\"emergencyLevelOfService\":2,\"substantiveLevelOfServiceCode\":4,\"substantiveLevelOfService\":3}"
      })
  void givenMixedCodesInEitherFieldOrder_whenRead_thenBothStoredValuesArePreserved(String json)
      throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    Proceeding proceeding = mapper.readValue(json, Proceeding.class);

    assertThat(proceeding.getSubstantiveLevelOfServiceCode()).isEqualTo(4);
    assertThat(proceeding.getEmergencyLevelOfServiceCode()).isEqualTo(9);
    assertThat(proceeding.getSubstantiveLevelOfService()).isEqualTo(3);
    assertThat(proceeding.getEmergencyLevelOfService()).isEqualTo(2);
    assertThat(mapper.valueToTree(proceeding).has("resolvedSubstantiveLevelOfServiceCode"))
        .isFalse();
  }

  @Test
  void givenPreEnhancementPayload_whenReadAndCopied_thenPreservesLegacyContracts()
      throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    String json =
        """
        {"serialisedRequest":"legacy-request","proceedings":[{
          "substantiveLevelOfService":3,"emergencyLevelOfService":2,
          "involvedChildren":[{"fullName":"Mary Jane Smith","dateOfBirth":"2020-01-01"}]
        }]}
        """;

    ApplicationDataPayload payload = mapper.readValue(json, ApplicationDataPayload.class);
    ApplicationDataPayload updated = payload.withNote("note", Instant.EPOCH);
    var stored = mapper.valueToTree(updated);

    assertThat(stored.at("/proceedings/0/substantiveLevelOfService").asInt()).isEqualTo(3);
    assertThat(stored.at("/proceedings/0/emergencyLevelOfService").asInt()).isEqualTo(2);
    assertThat(stored.at("/proceedings/0/involvedChildren/0/fullName").asText())
        .isEqualTo("Mary Jane Smith");
    assertThat(stored.at("/proceedings/0/involvedChildren/0/firstName").isNull()).isTrue();
    assertThat(updated.serialisedRequest()).isEqualTo("legacy-request");
    assertThat(payload.notes()).isEmpty();
    List<ApplicationDataPayload> copies =
        List.of(
            updated,
            payload.withAssignment("Assigned"),
            payload.withManualAssessmentRequired(),
            payload.withDecision(
                "GRANTED",
                uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState.MANUAL,
                Map.of(),
                null,
                "{}",
                "decision"),
            payload.withApplicationUpdate(
                payload.client(),
                payload.provider(),
                payload.opponents(),
                payload.submittedAt(),
                payload.usedDelegatedFunctions(),
                payload.categoryOfLaw(),
                payload.matterType(),
                payload.proceedings(),
                "{}",
                true));
    assertThat(copies)
        .allSatisfy(
            copy -> {
              var roundTrip =
                  mapper.readValue(mapper.writeValueAsString(copy), ApplicationDataPayload.class);
              assertThat(roundTrip.proceedings().getFirst().getSubstantiveLevelOfService())
                  .isEqualTo(3);
              assertThat(roundTrip.proceedings().getFirst().getEmergencyLevelOfService())
                  .isEqualTo(2);
              assertThat(
                      roundTrip
                          .proceedings()
                          .getFirst()
                          .getInvolvedChildren()
                          .getFirst()
                          .getFullName())
                  .isEqualTo("Mary Jane Smith");
              assertThat(
                      roundTrip
                          .proceedings()
                          .getFirst()
                          .getInvolvedChildren()
                          .getFirst()
                          .getFirstName())
                  .isNull();
            });
  }

  @Test
  void givenFilename_whenContentUpdated_thenEveryUpdatePreservesImmutableFilenameMap() {
    UUID documentId = UUID.randomUUID();
    ApplicationDataPayload original =
        ApplicationDataPayload.from(applicationCreationDetails(UUID.randomUUID()));
    ApplicationDataPayload payload = original.withDocumentFilename(documentId, "client-report.pdf");

    assertThat(original.documentFilenames()).isEmpty();
    List<ApplicationDataPayload> updates =
        List.of(
            payload.withAssignment("Assigned"),
            payload.withManualAssessmentRequired(),
            payload.withNote("note", Instant.now()),
            payload.withDecision(
                "GRANTED",
                uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState.MANUAL,
                Map.of(),
                null,
                "{}",
                "decision"),
            payload.withApplicationUpdate(
                payload.client(),
                payload.provider(),
                payload.opponents(),
                payload.submittedAt(),
                payload.usedDelegatedFunctions(),
                payload.categoryOfLaw(),
                payload.matterType(),
                payload.proceedings(),
                "{}",
                true));
    assertThat(updates)
        .allSatisfy(
            updated ->
                assertThat(updated.documentFilenames())
                    .containsExactlyEntriesOf(Map.of(documentId, "client-report.pdf")));
    assertThatThrownBy(() -> payload.documentFilenames().put(UUID.randomUUID(), "other.pdf"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void givenLegacyPayloadWithoutFilenameMap_whenDeserialized_thenNormalizesToEmpty()
      throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ObjectNode json =
        mapper.valueToTree(
            ApplicationDataPayload.from(applicationCreationDetails(UUID.randomUUID())));
    json.remove("documentFilenames");

    ApplicationDataPayload payload = mapper.treeToValue(json, ApplicationDataPayload.class);

    assertThat(payload.documentFilenames()).isEmpty();
  }

  private ApplicationDataRepository repository;
  private ApplicationDataStore store;

  @BeforeEach
  void setUp() {
    repository = mock(ApplicationDataRepository.class);
    store = new ApplicationDataStore(repository);
  }

  @Test
  void givenCreationDetails_whenAppended_thenStoresVersionedPayloadAndStableHash() {
    UUID applicationId = UUID.randomUUID();
    var details = applicationCreationDetails(applicationId);

    String hash = store.append(applicationId, 0L, details);

    ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(hash)
        .isEqualTo(ApplicationDataStore.fingerprint(details.serialisedRequest()))
        .hasSize(64);
    assertThat(captor.getValue().getId()).isEqualTo(new ApplicationDataId(applicationId, 0L));
    assertThat(captor.getValue().getPayload()).isEqualTo(ApplicationDataPayload.from(details));
    assertThat(captor.getValue().getPayloadHash()).isEqualTo(hash);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(details.occurredAt());
  }

  @Test
  void givenReconstructedPayload_whenAppended_thenStoresRequestedVersionAndRequestHash() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    Instant occurredAt = Instant.parse("2026-07-20T10:00:00Z");

    store.append(applicationId, 3L, payload, "decision-request", occurredAt);

    ArgumentCaptor<ApplicationData> captor = ArgumentCaptor.forClass(ApplicationData.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(new ApplicationDataId(applicationId, 3L));
    assertThat(captor.getValue().getPayload()).isSameAs(payload);
    assertThat(captor.getValue().getPayloadHash())
        .isEqualTo(ApplicationDataStore.fingerprint("decision-request"));
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenDuplicateVersion_whenRepositoryRejectsInsert_thenFailurePropagates() {
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate application data version"));

    assertThatThrownBy(
            () ->
                store.append(UUID.randomUUID(), 1L, applicationCreationDetails(UUID.randomUUID())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("duplicate application data version");
  }

  @Test
  void givenMissingVersion_whenRetrieved_thenReportsApplicationAndVersion() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findById(new ApplicationDataId(applicationId, 7L)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.get(applicationId, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application data not found for " + applicationId + " version 7");
  }

  @Test
  void givenStoredApplicationData_whenLatestVersionRetrieved_thenReturnsHighestVersion() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataPayload payload =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    when(repository.findFirstByIdApplicationIdOrderByIdVersionDesc(applicationId))
        .thenReturn(
            Optional.of(
                ApplicationData.builder()
                    .id(new ApplicationDataId(applicationId, 3L))
                    .payload(payload)
                    .build()));

    assertThat(store.latestVersion(applicationId)).isEqualTo(3L);
  }

  @Test
  void givenNoStoredApplicationData_whenLatestVersionRetrieved_thenReportsApplication() {
    UUID applicationId = UUID.randomUUID();
    when(repository.findFirstByIdApplicationIdOrderByIdVersionDesc(applicationId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> store.latestVersion(applicationId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application data not found for " + applicationId);
  }

  @Test
  void givenRequestedVersions_whenRetrieved_thenReturnsOnlyStoredPayloadsByIdentifier() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDataId firstId = new ApplicationDataId(applicationId, 0L);
    ApplicationDataId secondId = new ApplicationDataId(applicationId, 1L);
    ApplicationDataPayload first =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    ApplicationDataPayload second = first.withAssignment("Assigned");
    when(repository.findAllById(List.of(firstId, secondId)))
        .thenReturn(
            List.of(
                ApplicationData.builder().id(firstId).payload(first).build(),
                ApplicationData.builder().id(secondId).payload(second).build()));

    var result = store.getAll(List.of(firstId, secondId));

    assertThat(result).containsEntry(firstId, first).containsEntry(secondId, second);
  }

  @Test
  void
      givenWrappedJacksonFailure_whenPayloadsRetrieved_thenLogsIdentifiersAndRethrowsOriginalException() {
    ApplicationDataId dataId = new ApplicationDataId(UUID.randomUUID(), 4L);
    UnrecognizedPropertyException jacksonException =
        UnrecognizedPropertyException.from(
            mock(JsonParser.class),
            ApplicationDataPayload.class,
            "emergencyLevelOfService",
            List.of());
    jacksonException.prependPath(ApplicationDataPayload.class, "proceedings");
    RuntimeException failure = new RuntimeException("Could not deserialize JSON", jacksonException);
    when(repository.findAllById(List.of(dataId))).thenThrow(failure);
    Logger logger = (Logger) LoggerFactory.getLogger(ApplicationDataStore.class);
    ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    logEvents.start();
    logger.addAppender(logEvents);

    try {
      assertThatThrownBy(() -> store.getAll(List.of(dataId))).isSameAs(failure);

      assertThat(logEvents.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                assertThat(event.getFormattedMessage())
                    .contains("application_data JSON deserialization failed")
                    .contains(dataId.toString())
                    .contains("emergencyLevelOfService")
                    .contains("proceedings.emergencyLevelOfService");
                assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(RuntimeException.class.getName());
              });
    } finally {
      logger.detachAppender(logEvents);
      logEvents.stop();
    }
  }

  @Test
  void givenKnownInput_whenFingerprinted_thenReturnsExpectedSha256Digest() {
    assertThat(ApplicationDataStore.fingerprint("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
