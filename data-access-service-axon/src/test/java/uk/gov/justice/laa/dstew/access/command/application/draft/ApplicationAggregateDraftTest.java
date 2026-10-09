package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.handler.ApplicationDocumentUploadCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.CreateApplicationDraftCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.SubmitApplicationDraftCommandHandler;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Integration tests for the Application draft commands on {@link ApplicationAggregate}, using the
 * Axon test fixture. Mirrors the equivalent Prior Authority draft tests.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationAggregateDraftTest {

  private AxonTestFixture fixture;
  @Mock private ApplicationDraftStore draftStore;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private ApplicationCreationDetailsFactory creationDetailsFactory;
  @Mock private JsonSchemaValidator jsonSchemaValidator;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
                .registerCommandHandlingModule(
                    CommandHandlingModule.named("application-draft-command-handlers")
                        .commandHandlers(
                            handlers ->
                                handlers
                                    .autodetectedCommandHandlingComponent(
                                        configuration -> new CreateApplicationDraftCommandHandler())
                                    .autodetectedCommandHandlingComponent(
                                        configuration -> new SubmitApplicationDraftCommandHandler())
                                    .autodetectedCommandHandlingComponent(
                                        configuration ->
                                            new ApplicationDocumentUploadCommandHandler())))
                .componentRegistry(
                    registry ->
                        registry
                            .registerComponent(
                                ApplicationDraftStore.class, configuration -> draftStore)
                            .registerComponent(
                                ApplicationDataStore.class, configuration -> applicationDataStore)
                            .registerComponent(
                                ApplicationCreationDetailsFactory.class,
                                configuration -> creationDetailsFactory)
                            .registerComponent(
                                JsonSchemaValidator.class, configuration -> jsonSchemaValidator)));
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  @Test
  void givenDraft_whenDocumentUploaded_thenStoresFilenameAndEmitsThinMetadata() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-10-02T10:00:00Z");
    ApplicationDraftPayload draft =
        new ApplicationDraftPayload("APPLICATION_SUBMITTED", "LAA-123", Map.of(), "{}", List.of());
    when(draftStore.find(applicationId)).thenReturn(Optional.of(draft));

    fixture
        .given()
        .events(new ApplicationDraftStartedEvent(applicationId, 1, "hash", uploadedAt))
        .when()
        .command(
            new ApplicationDocumentUploadCommand(
                applicationId,
                documentId,
                "GATEWAY_EVIDENCE",
                uploadedAt,
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY",
                "client-report.pdf"))
        .then()
        .resultMessagePayload(documentId)
        .events(
            new ApplicationDocumentUploadedEvent(
                applicationId,
                documentId,
                "GATEWAY_EVIDENCE",
                uploadedAt,
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY"));

    ArgumentCaptor<ApplicationDraftPayload> payload =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore).upsert(eq(applicationId), payload.capture(), eq("{}"), eq(uploadedAt));
    assertThat(payload.getValue().documentFilenames())
        .containsEntry(documentId, "client-report.pdf");
    verify(applicationDataStore, never()).append(any(), any(Long.class), any(), any(), any());
  }

  @Test
  void givenDraftWithFilename_whenSubmitted_thenCopiesFilenameIntoImmutableContent() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-02T10:00:00Z");
    ApplicationDraftPayload draft =
        new ApplicationDraftPayload(
                "APPLICATION_SUBMITTED",
                "LAA-123",
                validApplicationContent(applicationId, UUID.randomUUID()),
                "{}",
                List.of())
            .withDocumentFilename(documentId, "client-report.pdf");
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(draftStore.find(applicationId)).thenReturn(Optional.of(draft));
    when(creationDetailsFactory.prepare(any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(details);
    when(applicationDataStore.append(
            eq(applicationId),
            eq(0L),
            any(),
            eq(details.serialisedRequest()),
            eq(details.occurredAt())))
        .thenReturn("hash");

    fixture
        .given()
        .events(
            new ApplicationDraftStartedEvent(applicationId, 1, "hash", occurredAt),
            new ApplicationDocumentUploadedEvent(
                applicationId,
                documentId,
                "GATEWAY_EVIDENCE",
                occurredAt,
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY"))
        .when()
        .command(new SubmitApplicationDraftCommand(applicationId, occurredAt.plusSeconds(1)))
        .then()
        .resultMessagePayload(applicationId)
        .events(
            new ApplicationCreatedEvent(
                applicationId,
                0L,
                "hash",
                details.status(),
                details.schemaVersion(),
                details.occurredAt(),
                details.potentialDuplicates(),
                details.provider().getOfficeCode()));

    ArgumentCaptor<ApplicationDataPayload> payload =
        ArgumentCaptor.forClass(ApplicationDataPayload.class);
    verify(applicationDataStore)
        .append(
            eq(applicationId),
            eq(0L),
            payload.capture(),
            eq(details.serialisedRequest()),
            eq(details.occurredAt()));
    assertThat(payload.getValue().documentFilenames())
        .containsEntry(documentId, "client-report.pdf");
    verify(draftStore).delete(applicationId);
  }

  @Test
  void givenLegacyDraftPayload_whenFilenameAdded_thenLeavesOriginalContentUnchanged()
      throws Exception {
    UUID documentId = UUID.randomUUID();
    ApplicationDraftPayload legacy =
        new ObjectMapper()
            .readValue(
                """
                {"status":"APPLICATION_SUBMITTED","laaReference":"LAA-123",
                 "applicationContent":{},"serialisedRequest":"{}","potentialDuplicates":[]}
                """,
                ApplicationDraftPayload.class);

    ApplicationDraftPayload updated = legacy.withDocumentFilename(documentId, "client-report.pdf");

    assertThat(legacy.documentFilenames()).isEmpty();
    assertThat(updated.documentFilenames()).containsEntry(documentId, "client-report.pdf");
  }

  @Test
  void givenDraftWithoutContent_whenDocumentUploaded_thenRejects() {
    UUID applicationId = UUID.randomUUID();
    when(draftStore.find(applicationId)).thenReturn(Optional.empty());

    fixture
        .given()
        .events(new ApplicationDraftStartedEvent(applicationId, 1, "hash", Instant.now()))
        .when()
        .command(
            new ApplicationDocumentUploadCommand(
                applicationId,
                UUID.randomUUID(),
                "GATEWAY_EVIDENCE",
                Instant.now(),
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY",
                "report.pdf"))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
    verify(draftStore, never()).upsert(any(), any(), any(), any());
  }

  @Test
  void givenNoApplication_whenCreateDraft_thenWritesDraftAndEmitsDraftStartedEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    Map<String, Object> applicationContent =
        validApplicationContent(applicationId, UUID.randomUUID());

    when(draftStore.upsert(eq(applicationId), any(), eq(serialisedRequest), eq(occurredAt)))
        .thenReturn(fingerprint);

    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            "LAA-123",
            applicationContent,
            serialisedRequest,
            1,
            "BaseCivilApplication.json",
            occurredAt,
            List.of());

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .resultMessagePayload(applicationId)
        .events(
            new ApplicationDraftStartedEvent(applicationId, 1, fingerprint, occurredAt, "1A001B"));

    ArgumentCaptor<ApplicationDraftPayload> payloadCaptor =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore)
        .upsert(eq(applicationId), payloadCaptor.capture(), eq(serialisedRequest), eq(occurredAt));
    assertThat(payloadCaptor.getValue().status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(payloadCaptor.getValue().laaReference()).isEqualTo("LAA-123");
  }

  @Test
  void
      givenDraftInProgress_whenCreateDraftAgainWithDifferentContent_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(
            applicationId, 1, PayloadFingerprint.compute("{\"original\":true}"), occurredAt);

    CreateApplicationDraftCommand duplicateCommand =
        new CreateApplicationDraftCommand(
            applicationId,
            null,
            null,
            Map.of(),
            "{}",
            1,
            "BaseCivilApplication.json",
            occurredAt.plusSeconds(60),
            List.of());

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(duplicateCommand)
        .then()
        .exception(ApplicationCreationConflictException.class)
        .noEvents();

    verify(draftStore, never()).upsert(any(), any(), any(), any());
  }

  @Test
  void
      givenDraftInProgress_whenCreateDraftAgainWithDifferentSchemaVersion_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, fingerprint, occurredAt);

    CreateApplicationDraftCommand retryWithDifferentSchemaVersion =
        new CreateApplicationDraftCommand(
            applicationId,
            null,
            null,
            Map.of(),
            serialisedRequest,
            2,
            "BaseCivilApplication.json",
            occurredAt.plusSeconds(60),
            List.of());

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(retryWithDifferentSchemaVersion)
        .then()
        .exception(ApplicationCreationConflictException.class)
        .noEvents();

    verify(draftStore, never()).upsert(any(), any(), any(), any());
  }

  @Test
  void givenDraftInProgress_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, fingerprint, occurredAt);

    CreateApplicationDraftCommand retryCommand =
        new CreateApplicationDraftCommand(
            applicationId,
            null,
            null,
            Map.of(),
            serialisedRequest,
            1,
            "BaseCivilApplication.json",
            occurredAt.plusSeconds(60),
            List.of());

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(retryCommand)
        .then()
        .resultMessagePayload(applicationId)
        .noEvents();

    verify(draftStore, never()).upsert(any(), any(), any(), any());
  }

  @Test
  void givenFullyCreatedApplication_whenCreateDraft_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existingEvent = applicationCreatedEvent(applicationId);

    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId,
            null,
            null,
            Map.of(),
            "{}",
            1,
            "BaseCivilApplication.json",
            Instant.now(),
            List.of());

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(ApplicationCreationConflictException.class)
        .noEvents();

    verify(draftStore, never()).upsert(any(), any(), any(), any());
  }

  @Test
  void
      givenDraftInProgress_whenSubmit_thenAppendsVersion0AndEmitsApplicationCreatedEventAndDeletesDraft() {
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    String serialisedRequest = "{}";
    Map<String, Object> applicationContent =
        validApplicationContent(applicationId, UUID.randomUUID());
    ApplicationDraftPayload draftPayload =
        new ApplicationDraftPayload(
            "APPLICATION_SUBMITTED", "LAA-123", applicationContent, serialisedRequest, List.of());
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(
            applicationId, 1, PayloadFingerprint.compute(serialisedRequest), startedAt);
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    String fingerprint = PayloadFingerprint.compute(details.serialisedRequest());

    when(draftStore.find(applicationId)).thenReturn(Optional.of(draftPayload));
    when(creationDetailsFactory.prepare(any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(details);
    when(applicationDataStore.append(
            eq(applicationId),
            eq(0L),
            eq(ApplicationDataPayload.from(details)),
            eq(details.serialisedRequest()),
            eq(details.occurredAt())))
        .thenReturn(fingerprint);

    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, submittedAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .resultMessagePayload(applicationId)
        .events(
            new ApplicationCreatedEvent(
                applicationId,
                0L,
                fingerprint,
                details.status(),
                details.schemaVersion(),
                details.occurredAt(),
                details.potentialDuplicates(),
                details.provider().getOfficeCode()));

    verify(applicationDataStore)
        .append(
            applicationId,
            0L,
            ApplicationDataPayload.from(details),
            details.serialisedRequest(),
            details.occurredAt());
    verify(draftStore).delete(applicationId);
  }

  @Test
  void givenNoDraft_whenSubmit_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, Instant.now());

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  @Test
  void givenAlreadyCreatedApplication_whenSubmitDraft_thenThrowsResourceNotFound() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existingEvent = applicationCreatedEvent(applicationId);

    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, Instant.now());

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();

    verify(draftStore, never()).delete(any());
  }
}
