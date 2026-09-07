package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityStatus;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.util.PayloadFingerprint;

/** Integration tests for {@link PriorAuthorityAggregate} using the Axon test fixture. */
class PriorAuthorityAggregateTest {

  private AxonTestFixture fixture;
  private PriorAuthorityDataStore dataStore;

  @BeforeEach
  void setUp() {
    dataStore = mock(PriorAuthorityDataStore.class);
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(
                        UUID.class, PriorAuthorityAggregate.class))
                .componentRegistry(
                    registry ->
                        registry.registerComponent(
                            PriorAuthorityDataStore.class, configuration -> dataStore)));
  }

  @Test
  void givenNewAggregate_whenCreate_thenPersistsVersion0AndEmitsCreatedEvent() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityContent content =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null);
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);

    when(dataStore.append(
            eq(submissionId),
            eq(0L),
            eq(applicationId),
            any(),
            eq(serialisedRequest),
            eq(occurredAt)))
        .thenReturn(fingerprint);

    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId,
            applicationId,
            "EXPERT",
            content,
            serialisedRequest,
            1,
            "pa-schema",
            occurredAt);

    fixture
        .given()
        .noPriorActivity()
        .when()
        .command(command)
        .then()
        .events(
            new PriorAuthorityCreatedEvent(
                submissionId,
                applicationId,
                "EXPERT",
                0L,
                fingerprint,
                PriorAuthorityStatus.PENDING.name(),
                1,
                occurredAt));

    // Verify version-0 payload was persisted with correct structure
    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(dataStore)
        .append(
            eq(submissionId),
            eq(0L),
            eq(applicationId),
            payloadCaptor.capture(),
            eq(serialisedRequest),
            eq(occurredAt));
    PriorAuthorityDataPayload persisted = payloadCaptor.getValue();
    assertThat(persisted.submissionId()).isEqualTo(submissionId);
    assertThat(persisted.applicationId()).isEqualTo(applicationId);
    assertThat(persisted.content()).isEqualTo(content);
    assertThat(persisted.serialisedRequest()).isEqualTo(serialisedRequest);
    assertThat(persisted.submittedAt()).isEqualTo(occurredAt);
  }

  @Test
  void givenExistingAggregate_whenIdenticalSerialisedRequest_thenEmitsNoEventAndNeverCallsAppend() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String serialisedRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    String fingerprint = PayloadFingerprint.compute(serialisedRequest);

    PriorAuthorityCreatedEvent existingEvent =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "EXPERT",
            0L,
            fingerprint,
            PriorAuthorityStatus.PENDING.name(),
            1,
            occurredAt);

    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId,
            applicationId,
            "EXPERT",
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            serialisedRequest,
            1,
            "pa-schema",
            occurredAt);

    fixture.given().events(existingEvent).when().command(command).then().noEvents();

    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void
      givenExistingAggregate_whenDifferentSerialisedRequest_thenThrowsConflictAndNeverCallsAppend() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    String originalRequest = "{\"priorAuthorityType\":\"EXPERT\"}";
    String fingerprint = PayloadFingerprint.compute(originalRequest);

    PriorAuthorityCreatedEvent existingEvent =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "EXPERT",
            0L,
            fingerprint,
            PriorAuthorityStatus.PENDING.name(),
            1,
            occurredAt);

    CreatePriorAuthorityCommand command =
        new CreatePriorAuthorityCommand(
            submissionId,
            applicationId,
            "COUNSEL",
            new PriorAuthorityContent(PriorAuthorityType.COUNSEL, null, null, null, null),
            "{\"priorAuthorityType\":\"COUNSEL\"}",
            1,
            "pa-schema",
            occurredAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .exception(PriorAuthorityCreationConflictException.class)
        .noEvents();

    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void givenPendingPriorAuthority_whenDecisionRecorded_thenPersistsNextVersionAndEmitsEvent() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
    Instant decidedAt = Instant.parse("2026-08-01T11:00:00Z");
    PriorAuthorityCreatedEvent existingEvent =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "EXPERT",
            0L,
            "fp",
            PriorAuthorityStatus.PENDING.name(),
            1,
            createdAt);
    PriorAuthorityDataPayload current =
        new PriorAuthorityDataPayload(
            submissionId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Need expert", null, null, null),
            "{}",
            createdAt);
    when(dataStore.get(submissionId, 0L)).thenReturn(current);
    when(dataStore.append(
            eq(submissionId),
            eq(1L),
            eq(applicationId),
            any(),
            eq("{\"overallDecision\":\"GRANTED\"}"),
            eq(decidedAt)))
        .thenReturn("decision-fingerprint");

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId,
            "GRANTED",
            "{\"overallDecision\":\"GRANTED\"}",
            "Decision recorded",
            decidedAt);

    fixture
        .given()
        .events(existingEvent)
        .when()
        .command(command)
        .then()
        .events(
            new PriorAuthorityDecisionRecordedEvent(
                submissionId, applicationId, 1L, PriorAuthorityStatus.GRANTED.name(), decidedAt));

    ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor =
        ArgumentCaptor.forClass(PriorAuthorityDataPayload.class);
    verify(dataStore)
        .append(
            eq(submissionId),
            eq(1L),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{\"overallDecision\":\"GRANTED\"}"),
            eq(decidedAt));
    assertThat(payloadCaptor.getValue().decision()).isEqualTo("GRANTED");
    assertThat(payloadCaptor.getValue().decisionJustification()).isEqualTo("Decision recorded");
    assertThat(payloadCaptor.getValue().decisionSerialisedRequest())
        .isEqualTo("{\"overallDecision\":\"GRANTED\"}");
  }

  @Test
  void givenAlreadyDecidedWithSamePayload_whenDecisionRetried_thenEmitsNoEventAndNoAppend() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityCreatedEvent created =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "EXPERT",
            0L,
            "fp",
            PriorAuthorityStatus.PENDING.name(),
            1,
            occurredAt);
    PriorAuthorityDecisionRecordedEvent decided =
        new PriorAuthorityDecisionRecordedEvent(
            submissionId,
            applicationId,
            1L,
            PriorAuthorityStatus.REFUSED.name(),
            occurredAt.plusSeconds(1));
    when(dataStore.get(submissionId, 1L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                submissionId,
                applicationId,
                new PriorAuthorityContent(
                    PriorAuthorityType.EXPERT, "Need expert", null, null, null),
                "{}",
                occurredAt,
                "REFUSED",
                "Decision recorded",
                "{\"overallDecision\":\"REFUSED\"}"));

    MakePriorAuthorityDecisionCommand retry =
        new MakePriorAuthorityDecisionCommand(
            submissionId,
            "REFUSED",
            "{\"overallDecision\":\"REFUSED\"}",
            "Decision recorded",
            occurredAt.plusSeconds(2));

    fixture.given().events(created, decided).when().command(retry).then().noEvents();

    verify(dataStore, never()).append(any(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void givenAlreadyDecidedWithDifferentDecision_whenDecisionRecorded_thenThrowsConflict() {
    UUID submissionId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-01T10:00:00Z");
    PriorAuthorityCreatedEvent created =
        new PriorAuthorityCreatedEvent(
            submissionId,
            applicationId,
            "EXPERT",
            0L,
            "fp",
            PriorAuthorityStatus.PENDING.name(),
            1,
            occurredAt);
    PriorAuthorityDecisionRecordedEvent decided =
        new PriorAuthorityDecisionRecordedEvent(
            submissionId,
            applicationId,
            1L,
            PriorAuthorityStatus.GRANTED.name(),
            occurredAt.plusSeconds(1));
    when(dataStore.get(submissionId, 1L))
        .thenReturn(
            new PriorAuthorityDataPayload(
                submissionId,
                applicationId,
                new PriorAuthorityContent(
                    PriorAuthorityType.EXPERT, "Need expert", null, null, null),
                "{}",
                occurredAt,
                "GRANTED",
                "Decision recorded",
                "{\"overallDecision\":\"GRANTED\"}"));

    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            submissionId,
            "REFUSED",
            "{\"overallDecision\":\"REFUSED\"}",
            "Decision recorded",
            occurredAt.plusSeconds(2));

    fixture
        .given()
        .events(created, decided)
        .when()
        .command(command)
        .then()
        .exception(PriorAuthorityStatusConflictException.class)
        .noEvents();
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }
}
