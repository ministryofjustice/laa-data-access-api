package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Integration tests for {@link PriorAuthorityAggregate} using the Axon test fixture. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityAggregateTest {

  private AxonTestFixture fixture;
  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private JsonSchemaValidator jsonSchemaValidator;
  @Mock private EventAppender eventAppender;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(
                        UUID.class, PriorAuthorityAggregate.class))
                .componentRegistry(
                    registry ->
                        registry
                            .registerComponent(
                                PriorAuthorityDataStore.class, configuration -> dataStore)
                            .registerComponent(
                                PriorAuthorityDraftStore.class, configuration -> draftStore)
                            .registerComponent(
                                JsonSchemaValidator.class, configuration -> jsonSchemaValidator)));
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  @Test
  void givenNewAggregate_whenCreateDraft_thenWritesDraftAndEmitsDraftStartedEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityContent content = new PriorAuthorityContent(null, null, null, null, null);
    String serialisedRequest = "{}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);

    when(draftStore.upsert(
            eq(priorAuthorityId), eq(applicationId), any(), eq(serialisedRequest), eq(occurredAt)))
        .thenReturn(fingerprint);

    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            content,
            serialisedRequest,
            1,
            "PriorAuthority.json",
            occurredAt);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .events(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, null, 1, occurredAt));

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq(serialisedRequest),
            eq(occurredAt));
    PriorAuthorityDataPayload persisted = payloadCaptor.getValue();
    assertThat(persisted.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(persisted.applicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenDraftInProgress_whenUpdateDraft_thenPersistsDraftAndEmitsNoEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String firstRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    String secondRequest = "{\"justification\":\"need expert\"}";

    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, occurredAt);
    PriorAuthorityDataPayload existingDraftPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(null, null, null, null, null),
            firstRequest,
            occurredAt);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraftPayload));

    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, "need expert", null, null, null),
            secondRequest,
            1,
            "PriorAuthority.json",
            occurredAt);

    fixture.given().events(existingEvent).when().command(command).then().noEvents();

    verify(draftStore)
        .upsert(eq(priorAuthorityId), eq(applicationId), any(), eq(secondRequest), eq(occurredAt));
  }

  @Test
  void givenExistingPriorAuthority_whenCreateDraftAgain_thenThrowsConflictAndPersistsNothing() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, occurredAt);

    CreatePriorAuthorityDraftCommand duplicateCreateCommand =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(EXPERT, null, null, null, null),
            serialisedRequest,
            1,
            "PriorAuthority.json",
            occurredAt.plusSeconds(60));

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(duplicateCreateCommand)
        .then()
        .exception(PriorAuthorityCreationConflictException.class)
        .noEvents();

    verify(draftStore, never()).upsert(any(), any(), any(), any(), any());
  }

  @Test
  void givenStateTypeSet_whenUpdateDraftWithoutType_thenPersistsStateType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    PriorAuthorityContent existingContent = new PriorAuthorityContent(null, null, null, null, null);
    PriorAuthorityDataPayload existingDraftPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, serialisedRequest, occurredAt);
    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, occurredAt);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraftPayload));

    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, "updated justification", null, null, null),
            "{\"justification\":\"updated justification\"}",
            1,
            "PriorAuthority.json",
            occurredAt);

    fixture.given().events(existingEvent).when().command(command).then().noEvents();

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{\"justification\":\"updated justification\"}"),
            eq(occurredAt));
    assertThat(payloadCaptor.getValue().content().priorAuthorityType()).isEqualTo(EXPERT);
  }

  @Test
  void givenStateTypeMissing_whenUpdateDraftWithoutType_thenThrowsNullPointerException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityDataPayload existingDraftPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(null, null, null, null, null),
            "{}",
            occurredAt);
    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(priorAuthorityId, applicationId, null, 1, occurredAt);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraftPayload));

    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, "updated justification", null, null, null),
            "{\"justification\":\"updated justification\"}",
            1,
            "PriorAuthority.json",
            occurredAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(NullPointerException.class)
        .noEvents();
  }

  @Test
  void givenDraftInProgress_whenSubmit_thenAppendsVersion0AndEmitsSubmittedEventAndDeletesDraft() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    PriorAuthorityContent content = new PriorAuthorityContent(EXPERT, null, null, null, null);
    PriorAuthorityDataPayload draftPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, serialisedRequest, startedAt);

    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(draftPayload));

    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, submittedAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .events(
            new PriorAuthoritySubmittedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, submittedAt));

    verify(jsonSchemaValidator).validate(content, "PriorAuthority.json", 1);
    verify(dataStore)
        .append(priorAuthorityId, 0L, applicationId, draftPayload, serialisedRequest, submittedAt);
    verify(draftStore).delete(priorAuthorityId);
  }

  @Test
  void
      givenSchemaInvalidDraft_whenSubmit_thenThrowsValidationExceptionAndNeitherAppendsNorDeletesDraft() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    PriorAuthorityContent content = new PriorAuthorityContent(EXPERT, null, null, null, null);
    PriorAuthorityDataPayload draftPayload =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, content, serialisedRequest, startedAt);

    PriorAuthorityDraftStartedEvent existingEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(draftPayload));
    doThrow(new ValidationException(List.of("expertDetails is required")))
        .when(jsonSchemaValidator)
        .validate(content, "PriorAuthority.json", 1);

    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, submittedAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(ValidationException.class)
        .noEvents();

    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
    verify(draftStore, never()).delete(any());
  }

  @Test
  void givenPendingSubmission_whenUpdateDraft_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";

    PriorAuthorityDraftStartedEvent draftStartedEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt);
    PriorAuthoritySubmittedEvent submittedEvent =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, submittedAt);

    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            submittedAt);

    fixture
        .given()
        .events(draftStartedEvent, submittedEvent)
        .when()
        .command(command)
        .then()
        .exception(ResourceNotFoundException.class)
        .noEvents();
  }

  @Test
  void givenNeverSeenPriorAuthorityId_whenUpdateDraft_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();

    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            Instant.parse("2026-08-01T10:00:00Z"));

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
  void givenNoDraftInProgress_whenSubmit_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();

    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, Instant.now());

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
  void givenDraftWithoutExistingDocuments_whenUpload_thenPersistsSingleUploadedDocument() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            file,
            "gateway_evidence",
            "CIVIL_APPLY",
            "sum",
            "{}",
            occurredAt);

    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(EXPERT, "why", null, null, null),
                    "{}",
                    occurredAt)));

    UUID returnedDocumentId = aggregate.handle(command, draftStore, eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{}"),
            eq(occurredAt));
    assertThat(payloadCaptor.getValue().content().uploadedDocuments()).hasSize(1);
    assertThat(payloadCaptor.getValue().content().uploadedDocuments().getFirst().documentType())
        .isEqualTo("gateway_evidence");
    assertThat(payloadCaptor.getValue().content().uploadedDocuments().getFirst().checksum())
        .isEqualTo("sum");
    verify(eventAppender).append(any(PriorAuthorityDocumentUploadedEvent.class));
  }

  @Test
  void givenDraftWithExistingDocument_whenUpload_thenAppendsToDocumentList() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    MockMultipartFile file =
        new MockMultipartFile("file", "second.pdf", "application/pdf", "content".getBytes());
    UUID documentId = UUID.randomUUID();

    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(
                        EXPERT,
                        "why",
                        null,
                        null,
                        null,
                        List.of(
                            new PriorAuthorityDocument(
                                UUID.randomUUID(),
                                "gateway_evidence",
                                "first.pdf",
                                "PDF",
                                "application/pdf",
                                1L,
                                occurredAt,
                                "CIVIL_APPLY",
                                "first-checksum"))),
                    "{}",
                    occurredAt)));

    aggregate.handle(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            file,
            "gateway_evidence",
            "CIVIL_APPLY",
            "sum",
            "{}",
            occurredAt),
        draftStore,
        eventAppender);

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{}"),
            eq(occurredAt));
    assertThat(payloadCaptor.getValue().content().uploadedDocuments()).hasSize(2);
  }

  @Test
  void givenMissingDraft_whenUpload_thenThrowsNotFound() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    MockMultipartFile file =
        new MockMultipartFile("file", "missing.pdf", "application/pdf", "content".getBytes());
    UUID documentId = UUID.randomUUID();

    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, occurredAt));
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    new PriorAuthorityDocumentUploadCommand(
                        priorAuthorityId,
                        documentId,
                        file,
                        "gateway_evidence",
                        "CIVIL_APPLY",
                        "sum",
                        "{}",
                        occurredAt),
                    draftStore,
                    eventAppender));
  }

  @Test
  void givenChecksumMissing_whenUpload_thenEmitsEventWithNullChecksum() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    MockMultipartFile file =
        new MockMultipartFile("file", "nullsum.pdf", "application/pdf", "content".getBytes());
    UUID documentId = UUID.randomUUID();

    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(EXPERT, "why", null, null, null),
                    "{}",
                    occurredAt)));

    aggregate.handle(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            file,
            "gateway_evidence",
            "CIVIL_APPLY",
            null,
            "{}",
            occurredAt),
        draftStore,
        eventAppender);

    ArgumentCaptor<PriorAuthorityDocumentUploadedEvent> eventCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentUploadedEvent.class);
    verify(eventAppender).append(eventCaptor.capture());
    assertThat(eventCaptor.getValue().checksum()).isNull();
  }
}
