package uk.gov.justice.laa.dstew.access.command.application.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
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
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

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
  void givenNoApplication_whenCreateDraft_thenWritesDraftAndEmitsDraftStartedEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);

    when(draftStore.upsert(eq(applicationId), any(), eq(serialisedRequest), eq(occurredAt)))
        .thenReturn(fingerprint);

    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            "LAA-123",
            Map.of(),
            serialisedRequest,
            1,
            occurredAt);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .resultMessagePayload(applicationId)
        .events(new ApplicationDraftStartedEvent(applicationId, 1, occurredAt));

    ArgumentCaptor<ApplicationDraftPayload> payloadCaptor =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore)
        .upsert(eq(applicationId), payloadCaptor.capture(), eq(serialisedRequest), eq(occurredAt));
    assertThat(payloadCaptor.getValue().status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(payloadCaptor.getValue().laaReference()).isEqualTo("LAA-123");
  }

  @Test
  void givenDraftInProgress_whenCreateDraftAgain_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, occurredAt);

    CreateApplicationDraftCommand duplicateCommand =
        new CreateApplicationDraftCommand(
            applicationId, null, null, Map.of(), "{}", 1, occurredAt.plusSeconds(60));

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
  void givenFullyCreatedApplication_whenCreateDraft_thenThrowsConflictAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existingEvent = applicationCreatedEvent(applicationId);

    CreateApplicationDraftCommand command =
        new CreateApplicationDraftCommand(
            applicationId, null, null, Map.of(), "{}", 1, Instant.now());

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
  void givenDraftInProgress_whenUpdateDraft_thenPersistsDraftAndEmitsDraftUpdatedEvent() {
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant occurredAt = Instant.parse("2026-08-02T10:00:00Z");
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, startedAt);

    when(draftStore.find(applicationId))
        .thenReturn(Optional.of(new ApplicationDraftPayload(null, null, Map.of(), "{}")));

    UpdateApplicationDraftCommand command =
        new UpdateApplicationDraftCommand(
            applicationId,
            "APPLICATION_SUBMITTED",
            "LAA-999",
            Map.of("key", "value"),
            "{\"key\":\"value\"}",
            occurredAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .events(new ApplicationDraftUpdatedEvent(applicationId, occurredAt));

    ArgumentCaptor<ApplicationDraftPayload> payloadCaptor =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore)
        .upsert(
            eq(applicationId), payloadCaptor.capture(), eq("{\"key\":\"value\"}"), eq(occurredAt));
    assertThat(payloadCaptor.getValue().laaReference()).isEqualTo("LAA-999");
  }

  @Test
  void givenNoDraft_whenUpdateDraft_thenThrowsResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();

    UpdateApplicationDraftCommand command =
        new UpdateApplicationDraftCommand(applicationId, null, null, Map.of(), "{}", Instant.now());

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
            "APPLICATION_SUBMITTED", "LAA-123", applicationContent, serialisedRequest);
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, startedAt);
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    String fingerprint = PayloadFingerprint.compute(details.serialisedRequest());

    when(draftStore.find(applicationId)).thenReturn(Optional.of(draftPayload));
    when(creationDetailsFactory.prepare(any(CreateApplicationCommand.class))).thenReturn(details);
    when(applicationDataStore.append(eq(applicationId), eq(0L), eq(details)))
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
                details.occurredAt()));

    verify(jsonSchemaValidator).validate(applicationContent, "BaseCivilApplication.json", 1);
    verify(applicationDataStore).append(applicationId, 0L, details);
    verify(draftStore).delete(applicationId);
  }

  @Test
  void
      givenSchemaInvalidDraft_whenSubmit_thenThrowsValidationExceptionAndNeitherAppendsNorDeletesDraft() {
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    Map<String, Object> applicationContent = Map.of();
    ApplicationDraftPayload draftPayload =
        new ApplicationDraftPayload("APPLICATION_SUBMITTED", "LAA-123", applicationContent, "{}");
    ApplicationDraftStartedEvent existingEvent =
        new ApplicationDraftStartedEvent(applicationId, 1, startedAt);

    when(draftStore.find(applicationId)).thenReturn(Optional.of(draftPayload));
    doThrow(new ValidationException(java.util.List.of("invalid")))
        .when(jsonSchemaValidator)
        .validate(applicationContent, "BaseCivilApplication.json", 1);

    SubmitApplicationDraftCommand command =
        new SubmitApplicationDraftCommand(applicationId, submittedAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(ValidationException.class)
        .noEvents();

    verify(applicationDataStore, never()).append(any(), anyLong(), any());
    verify(draftStore, never()).delete(any());
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
  void givenAlreadyCreatedApplication_whenSubmitDraft_thenThrowsConflict() {
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
        .exception(ApplicationCreationConflictException.class)
        .noEvents();

    verify(draftStore, never()).delete(any());
  }
}
