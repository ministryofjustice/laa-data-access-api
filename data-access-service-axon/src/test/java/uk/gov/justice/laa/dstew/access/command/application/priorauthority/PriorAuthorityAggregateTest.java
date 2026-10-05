package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.ApportionmentInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Integration tests for {@link PriorAuthorityAggregate} using the Axon test fixture. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityAggregateTest {

  private AxonTestFixture fixture;
  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private ApplicationDataStore applicationDataStore;
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
                                ApplicationDataStore.class, configuration -> applicationDataStore)
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
  void givenDraftInProgress_whenUpdateDraft_thenPersistsDraftAndEmitsDraftUpdatedEvent() {
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

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .events(new PriorAuthorityDraftUpdatedEvent(priorAuthorityId, applicationId, occurredAt));

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

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .events(new PriorAuthorityDraftUpdatedEvent(priorAuthorityId, applicationId, occurredAt));

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
    when(applicationDataStore.latestVersion(applicationId)).thenReturn(7L);

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
                priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, 7L, submittedAt));

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

    PriorAuthorityDraftStartedEvent draftStartedEvent =
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt);
    PriorAuthoritySubmittedEvent submittedEvent =
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, 0L, submittedAt);

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
  void givenSubmittedPriorAuthority_whenDecisionMade_thenPersistsNextVersionAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    Instant decidedAt = Instant.parse("2026-08-02T11:00:00Z");
    ExpertFeeInformation expertFee =
        ExpertFeeInformation.builder()
            .newFixedRateAmount(BigDecimal.valueOf(250.0))
            .newHourlyRateAmount(BigDecimal.valueOf(125.0))
            .build();
    DisbursementInformation disbursementInformation =
        DisbursementInformation.builder().newAmount(BigDecimal.valueOf(75.5)).build();
    ApportionmentInformation apportionmentInformation =
        ApportionmentInformation.builder().newClientShareAmount(BigDecimal.valueOf(10.25)).build();
    PriorAuthorityDataPayload current =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(EXPERT, "Need expert", null, null, null),
            "{}",
            submittedAt);
    when(dataStore.get(priorAuthorityId, 0L)).thenReturn(current);

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(1234.56),
            expertFee,
            disbursementInformation,
            apportionmentInformation,
            decidedAt,
            "{\"decision\":\"GRANTED\"}",
            decidedAt);

    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    aggregate.on(
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt));
    aggregate.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, 0L, submittedAt));
    aggregate.on(
        new WorkItemAssigned(
            priorAuthorityId,
            WorkItemType.PRIOR_AUTHORITY,
            0L,
            1L,
            TestJwtDecoderConfig.CASEWORKER_ID,
            submittedAt));

    aggregate.handle(command, dataStore, eventAppender);

    verify(eventAppender)
        .append(
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                EXPERT.name(),
                1L,
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(1234.56),
                decidedAt,
                decidedAt));

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(dataStore)
        .append(
            eq(priorAuthorityId),
            eq(1L),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{\"decision\":\"GRANTED\"}"),
            eq(decidedAt));

    PriorAuthorityDataPayload persisted = payloadCaptor.getValue();
    assertThat(persisted.decision()).isEqualTo("GRANTED");
    assertThat(persisted.decisionJustification()).isEqualTo("Decision recorded");
    assertThat(persisted.amountGranted()).isEqualByComparingTo(BigDecimal.valueOf(1234.56));
    assertThat(persisted.dateGranted()).isEqualTo(decidedAt);
    assertThat(persisted.expert()).isEqualTo(expertFee);
    assertThat(persisted.disbursement()).isEqualTo(disbursementInformation);
    assertThat(persisted.apportionment()).isEqualTo(apportionmentInformation);
    assertThat(persisted.decisionSerialisedRequest()).isEqualTo("{\"decision\":\"GRANTED\"}");
  }

  @Test
  void givenAlreadyDecidedPriorAuthority_whenSameDecisionMade_thenThrowsConflictAndDoesNotAppend() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    Instant firstDecisionAt = Instant.parse("2026-08-02T11:00:00Z");

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            1L,
            "GRANTED",
            "Initial",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            firstDecisionAt,
            "{\"decision\":\"GRANTED\"}",
            firstDecisionAt.plusSeconds(1));

    fixture
        .given()
        .events(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt),
            new PriorAuthoritySubmittedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, 0L, submittedAt),
            new WorkItemAssigned(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                0L,
                1L,
                TestJwtDecoderConfig.CASEWORKER_ID,
                submittedAt),
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                EXPERT.name(),
                1L,
                "GRANTED",
                "Initial",
                BigDecimal.valueOf(100.0),
                firstDecisionAt,
                firstDecisionAt))
        .when()
        .command(command)
        .then()
        .exception(PriorAuthorityStatusConflictException.class)
        .noEvents();

    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void givenAlreadyDecidedPriorAuthority_whenDifferentDecisionMade_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant submittedAt = Instant.parse("2026-08-02T10:00:00Z");
    Instant firstDecisionAt = Instant.parse("2026-08-02T11:00:00Z");

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            1L,
            "REFUSED",
            "Changed",
            BigDecimal.ZERO,
            null,
            null,
            null,
            firstDecisionAt,
            "{\"decision\":\"REFUSED\"}",
            firstDecisionAt.plusSeconds(1));

    fixture
        .given()
        .events(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt),
            new PriorAuthoritySubmittedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, 0L, 0L, submittedAt),
            new WorkItemAssigned(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                0L,
                1L,
                TestJwtDecoderConfig.CASEWORKER_ID,
                submittedAt),
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                EXPERT.name(),
                1L,
                "GRANTED",
                "Initial",
                BigDecimal.valueOf(100.0),
                firstDecisionAt,
                firstDecisionAt))
        .when()
        .command(command)
        .then()
        .exception(PriorAuthorityStatusConflictException.class)
        .noEvents();
  }

  @Test
  void givenNeverInitialized_whenMakePriorAuthorityDecision_thenThrowsResourceNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    Instant decidedAt = Instant.parse("2026-08-02T11:00:00Z");

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(1234.56),
            null,
            null,
            null,
            decidedAt,
            "{\"decision\":\"GRANTED\"}",
            decidedAt);

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
      givenDraftPriorAuthority_whenMakePriorAuthorityDecision_thenThrowsStatusConflictWithoutReadingDataStore() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant startedAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant decidedAt = Instant.parse("2026-08-02T11:00:00Z");

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(1234.56),
            null,
            null,
            null,
            decidedAt,
            "{\"decision\":\"GRANTED\"}",
            decidedAt);

    fixture
        .given()
        .events(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, EXPERT.name(), 1, startedAt),
            new WorkItemAssigned(
                priorAuthorityId,
                WorkItemType.PRIOR_AUTHORITY,
                0L,
                1L,
                TestJwtDecoderConfig.CASEWORKER_ID,
                startedAt))
        .when()
        .command(command)
        .then()
        .exception(PriorAuthorityStatusConflictException.class)
        .noEvents();

    verify(dataStore, never()).get(any(), anyLong());
    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void givenDraft_whenUpload_thenStoresFilenameInDraftAndEmitsFilenameFreeEvent() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(draft(priorAuthorityId, applicationId, occurredAt)));

    UUID returnedDocumentId =
        aggregate.handle(
            uploadCommand(priorAuthorityId, documentId, "evidence.pdf", occurredAt),
            draftStore,
            eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    InOrder calls = inOrder(draftStore, eventAppender);
    calls
        .verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{\"draft\":true}"),
            eq(occurredAt));
    calls
        .verify(eventAppender)
        .append(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
    assertThat(payloadCaptor.getValue().documentFilenames())
        .containsExactlyEntriesOf(Map.of(documentId, "evidence.pdf"));
    assertThat(payloadCaptor.getValue().serialisedRequest()).isEqualTo("{\"draft\":true}");
  }

  @Test
  void givenDraftWithExistingDocument_whenUpload_thenKeepsExistingFilename() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, firstId, applicationId, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                draft(priorAuthorityId, applicationId, occurredAt)
                    .withDocumentFilename(firstId, "first.pdf")));

    aggregate.handle(
        uploadCommand(priorAuthorityId, secondId, "second.pdf", occurredAt),
        draftStore,
        eventAppender);

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(draftStore).upsert(any(), any(), payloadCaptor.capture(), any(), any());
    assertThat(payloadCaptor.getValue().documentFilenames())
        .containsExactlyInAnyOrderEntriesOf(Map.of(firstId, "first.pdf", secondId, "second.pdf"));
  }

  @Test
  void givenMissingDraft_whenUpload_thenThrowsNotFound() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, UUID.randomUUID(), occurredAt));
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    uploadCommand(priorAuthorityId, UUID.randomUUID(), "missing.pdf", occurredAt),
                    draftStore,
                    eventAppender));

    verify(eventAppender, never()).append(any(PriorAuthorityDocumentUploadedEvent.class));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void givenMissingFilename_whenUpload_thenRejectsBeforeReadingDraft(String filename) {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, UUID.randomUUID(), occurredAt));

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    uploadCommand(priorAuthorityId, UUID.randomUUID(), filename, occurredAt),
                    draftStore,
                    eventAppender));

    verify(draftStore, never()).find(any());
    verify(eventAppender, never()).append(any(PriorAuthorityDocumentUploadedEvent.class));
  }

  @Test
  void givenRecordedDocumentId_whenUploadedAgain_thenRejectsWithoutWriting() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    uploadCommand(priorAuthorityId, documentId, "evidence.pdf", occurredAt),
                    draftStore,
                    eventAppender));

    verify(draftStore, never()).upsert(any(), any(), any(), any(), any());
    verify(eventAppender, never()).append(any(PriorAuthorityDocumentUploadedEvent.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPLOAD", "DELETE", "TYPE_UPDATE"})
  void givenSubmittedPriorAuthority_whenDocumentChanged_thenRejectsWithoutWriting(String change) {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
    aggregate.on(
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 0L, 0L, occurredAt));

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ValidationException.class)
        .isThrownBy(
            () -> {
              switch (change) {
                case "UPLOAD" ->
                    aggregate.handle(
                        uploadCommand(priorAuthorityId, UUID.randomUUID(), "new.pdf", occurredAt),
                        draftStore,
                        eventAppender);
                case "DELETE" ->
                    aggregate.handle(
                        new PriorAuthorityDocumentDeleteCommand(
                            priorAuthorityId, documentId, occurredAt),
                        draftStore,
                        eventAppender);
                default ->
                    aggregate.handle(
                        new PriorAuthorityDocumentTypeUpdateCommand(
                            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "{}", occurredAt),
                        eventAppender);
              }
            });

    verify(draftStore, never()).upsert(any(), any(), any(), any(), any());
    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenActiveDocument_whenUpdateDocumentType_thenEmitsEventWithoutTouchingDraft() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));

    assertThat(
            aggregate.handle(
                new PriorAuthorityDocumentTypeUpdateCommand(
                    priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "{}", occurredAt),
                eventAppender))
        .isEqualTo(documentId);

    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentTypeUpdatedEvent(
                priorAuthorityId, documentId, "GATEWAY_EVIDENCE", occurredAt));
    verifyNoInteractions(draftStore);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenUnknownOrDeletedDocument_whenUpdateDocumentType_thenThrowsNotFound(boolean known) {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    if (known) {
      aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
      aggregate.on(
          new PriorAuthorityDocumentDeletedEvent(
              priorAuthorityId, documentId, occurredAt, applicationId));
    }

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    new PriorAuthorityDocumentTypeUpdateCommand(
                        priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "{}", occurredAt),
                    eventAppender));

    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenInternalStringDocumentType_whenUpdateDocumentType_thenDoesNotDependOnApiEnum() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.now();
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "INTERNAL_TYPE", "{}", occurredAt);

    assertThat(aggregate.handle(command, eventAppender)).isEqualTo(documentId);

    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentTypeUpdatedEvent(
                priorAuthorityId, documentId, "INTERNAL_TYPE", occurredAt));
  }

  @Test
  void givenChecksumMissing_whenUpload_thenEmitsEventWithNullChecksum() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(Optional.of(draft(priorAuthorityId, applicationId, occurredAt)));

    aggregate.handle(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            UUID.randomUUID(),
            "CIVIL_APPLY",
            null,
            occurredAt,
            "nullsum.pdf",
            7L,
            "application/pdf"),
        draftStore,
        eventAppender);

    ArgumentCaptor<PriorAuthorityDocumentUploadedEvent> eventCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDocumentUploadedEvent.class);
    verify(eventAppender).append(eventCaptor.capture());
    assertThat(eventCaptor.getValue().checksum()).isNull();
  }

  @Test
  void givenActiveDocument_whenDelete_thenRemovesFilenameEmitsEventAndReturnsMetadata() {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UUID remainingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    Instant deletedAt = occurredAt.plusSeconds(60);
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
    aggregate.on(uploadedEvent(priorAuthorityId, remainingId, applicationId, occurredAt));
    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                draft(priorAuthorityId, applicationId, occurredAt)
                    .withDocumentFilename(documentId, "delete.pdf")
                    .withDocumentFilename(remainingId, "keep.pdf")));

    DocumentMetadata deleted =
        aggregate.handle(
            new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, deletedAt),
            draftStore,
            eventAppender);

    assertThat(deleted)
        .isEqualTo(
            new DocumentMetadata(
                documentId, null, occurredAt, 7L, "application/pdf", "sum", "CIVIL_APPLY", false));
    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    InOrder calls = inOrder(draftStore, eventAppender);
    calls
        .verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{\"draft\":true}"),
            eq(deletedAt));
    calls
        .verify(eventAppender)
        .append(
            new PriorAuthorityDocumentDeletedEvent(
                priorAuthorityId, documentId, deletedAt, applicationId));
    assertThat(payloadCaptor.getValue().documentFilenames())
        .containsExactlyEntriesOf(Map.of(remainingId, "keep.pdf"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void givenUnknownOrDeletedDocument_whenDelete_thenThrowsNotFoundWithoutWriting(boolean known) {
    PriorAuthorityAggregate aggregate = new PriorAuthorityAggregate();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-09-08T12:00:00Z");
    aggregate.on(draftStarted(priorAuthorityId, applicationId, occurredAt));
    if (known) {
      aggregate.on(uploadedEvent(priorAuthorityId, documentId, applicationId, occurredAt));
      aggregate.on(
          new PriorAuthorityDocumentDeletedEvent(
              priorAuthorityId, documentId, occurredAt, applicationId));
    }

    org.assertj.core.api.Assertions.assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                aggregate.handle(
                    new PriorAuthorityDocumentDeleteCommand(
                        priorAuthorityId, documentId, occurredAt),
                    draftStore,
                    eventAppender));

    verifyNoInteractions(draftStore, eventAppender);
  }

  private static PriorAuthorityDraftStartedEvent draftStarted(
      UUID priorAuthorityId, UUID applicationId, Instant occurredAt) {
    return new PriorAuthorityDraftStartedEvent(
        priorAuthorityId, applicationId, "EXPERT", 1, occurredAt);
  }

  private static PriorAuthorityDataPayload draft(
      UUID priorAuthorityId, UUID applicationId, Instant occurredAt) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId,
        applicationId,
        new PriorAuthorityContent(EXPERT, "why", null, null, null),
        "{\"draft\":true}",
        occurredAt);
  }

  private static PriorAuthorityDocumentUploadCommand uploadCommand(
      UUID priorAuthorityId, UUID documentId, String filename, Instant occurredAt) {
    return new PriorAuthorityDocumentUploadCommand(
        priorAuthorityId,
        documentId,
        "CIVIL_APPLY",
        "sum",
        occurredAt,
        filename,
        7L,
        "application/pdf");
  }

  private static PriorAuthorityDocumentUploadedEvent uploadedEvent(
      UUID priorAuthorityId, UUID documentId, UUID applicationId, Instant occurredAt) {
    return new PriorAuthorityDocumentUploadedEvent(
        priorAuthorityId,
        documentId,
        occurredAt,
        7L,
        "application/pdf",
        "sum",
        applicationId,
        "CIVIL_APPLY");
  }
}
