package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.modelling.repository.EntityNotFoundException;
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
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDecider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.handler.ApplicationDocumentUploadCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.CreateApplicationDraftCommandHandler;
import uk.gov.justice.laa.dstew.access.command.application.handler.SubmitApplicationDraftCommandHandler;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

/**
 * Integration tests for the Application draft commands on {@link ApplicationAggregate}, using the
 * Axon test fixture. Mirrors the equivalent Prior Authority draft tests.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationAggregateDraftTest {

  private AxonTestFixture fixture;
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
  void givenDraft_whenDocumentUploaded_thenEmitsMetadataWithFilenameAsContent() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-10-02T10:00:00Z");

    fixture
        .given()
        .events(draftStarted(applicationId, "hash", uploadedAt, Map.of()))
        .when()
        .command(uploadCommand(applicationId, documentId, uploadedAt))
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
                "CIVIL_APPLY",
                null,
                ".pdf",
                "client-report.pdf"));

    verifyNoInteractions(applicationDataStore);
  }

  @Test
  void givenDraftWithFilename_whenSubmitted_thenPreparesFromEventContentAndCopiesFilename() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-02T10:00:00Z");
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    when(creationDetailsFactory.prepare(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            content,
            "{}",
            1,
            List.of(new PotentialDuplicate("LAA-456"))))
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
            draftStarted(applicationId, "hash", occurredAt, content),
            new ApplicationDocumentUploadedEvent(
                applicationId,
                documentId,
                "GATEWAY_EVIDENCE",
                occurredAt,
                12L,
                "application/pdf",
                "checksum",
                "CIVIL_APPLY",
                null,
                ".pdf",
                "client-report.pdf"))
        .when()
        .command(new SubmitApplicationDraftCommand(applicationId, occurredAt.plusSeconds(1)))
        .then()
        .resultMessagePayload(applicationId)
        .events(ApplicationDecider.decideSubmitDraft(applicationId, 0L, "hash", details));

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
  }

  @Test
  void givenDraftContentErased_whenDocumentUploaded_thenRejectsAsNotFound() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(erasedDraftStarted(applicationId))
        .when()
        .command(uploadCommand(applicationId, UUID.randomUUID(), Instant.now()))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  @Test
  void givenDraftContentErased_whenSubmitted_thenRejectsAsNotFoundWithoutWritingContent() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(erasedDraftStarted(applicationId))
        .when()
        .command(new SubmitApplicationDraftCommand(applicationId, Instant.now()))
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();

    verifyNoInteractions(applicationDataStore, creationDetailsFactory);
  }

  @Test
  void givenDraftContentErased_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given()
        .events(erasedDraftStarted(applicationId))
        .when()
        .command(
            new CreateApplicationDraftCommand(
                applicationId,
                null,
                null,
                Map.of(),
                "{}",
                1,
                "BaseCivilApplication.json",
                Instant.now(),
                List.of()))
        .then()
        .resultMessagePayload(applicationId)
        .noEvents();
  }

  @Test
  void givenNoApplication_whenCreateDraft_thenEmitsDraftStartedEventCarryingContent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);
    Map<String, Object> applicationContent =
        validApplicationContent(applicationId, UUID.randomUUID());
    List<PotentialDuplicate> duplicates = List.of(new PotentialDuplicate("LAA-456"));

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
            duplicates);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .resultMessagePayload(applicationId)
        .events(
            new ApplicationDraftStartedEvent(
                applicationId,
                1,
                fingerprint,
                occurredAt,
                "1A001B",
                "APPLICATION_SUBMITTED",
                "LAA-123",
                applicationContent,
                serialisedRequest,
                duplicates));
  }

  @Test
  void
      givenDraftInProgress_whenCreateDraftAgainWithDifferentContent_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    ApplicationDraftStartedEvent existingEvent =
        draftStarted(
            applicationId, PayloadFingerprint.compute("{\"original\":true}"), occurredAt, Map.of());

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
  }

  @Test
  void
      givenDraftInProgress_whenCreateDraftAgainWithDifferentSchemaVersion_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    ApplicationDraftStartedEvent existingEvent =
        draftStarted(
            applicationId, PayloadFingerprint.compute(serialisedRequest), occurredAt, Map.of());

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
  }

  @Test
  void givenDraftInProgress_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    ApplicationDraftStartedEvent existingEvent =
        draftStarted(
            applicationId, PayloadFingerprint.compute(serialisedRequest), occurredAt, Map.of());

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
  }

  @Test
  void givenDraftInProgress_whenSubmit_thenAppendsVersion0AndEmitsApplicationCreatedEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    Map<String, Object> applicationContent =
        validApplicationContent(applicationId, UUID.randomUUID());
    ApplicationDraftStartedEvent existingEvent =
        draftStarted(
            applicationId, PayloadFingerprint.compute("{}"), startedAt, applicationContent);
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    String fingerprint = PayloadFingerprint.compute(details.serialisedRequest());

    when(creationDetailsFactory.prepare(any(), any(), any(), any(), anyInt(), any()))
        .thenReturn(details);
    when(applicationDataStore.append(
            eq(applicationId),
            eq(0L),
            eq(ApplicationDataPayload.from(details)),
            eq(details.serialisedRequest()),
            eq(details.occurredAt())))
        .thenReturn(fingerprint);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(new SubmitApplicationDraftCommand(applicationId, submittedAt))
        .then()
        .resultMessagePayload(applicationId)
        .events(ApplicationDecider.decideSubmitDraft(applicationId, 0L, fingerprint, details));

    verify(applicationDataStore)
        .append(
            applicationId,
            0L,
            ApplicationDataPayload.from(details),
            details.serialisedRequest(),
            details.occurredAt());
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
        .exception(EntityNotFoundException.class)
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

    verifyNoInteractions(applicationDataStore, creationDetailsFactory);
  }

  private static ApplicationDraftStartedEvent draftStarted(
      UUID applicationId, String fingerprint, Instant occurredAt, Map<String, Object> content) {
    return new ApplicationDraftStartedEvent(
        applicationId,
        1,
        fingerprint,
        occurredAt,
        null,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        content,
        "{}",
        List.of(new PotentialDuplicate("LAA-456")));
  }

  private static ApplicationDraftStartedEvent erasedDraftStarted(UUID applicationId) {
    return new ApplicationDraftStartedEvent(
        applicationId,
        1,
        PayloadFingerprint.compute("{}"),
        Instant.parse("2026-10-02T09:00:00Z"),
        null,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Erasable.erased(),
        Erasable.erased(),
        Erasable.erased());
  }

  private static ApplicationDocumentUploadCommand uploadCommand(
      UUID applicationId, UUID documentId, Instant uploadedAt) {
    return new ApplicationDocumentUploadCommand(
        applicationId,
        documentId,
        "GATEWAY_EVIDENCE",
        uploadedAt,
        12L,
        "application/pdf",
        "checksum",
        "CIVIL_APPLY",
        "client-report.pdf");
  }
}
