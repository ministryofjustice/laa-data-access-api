package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void givenCommand_whenDecideStartDraft_thenReturnsEventWithExpectedFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);
    PriorAuthorityDraftStartedEvent event = PriorAuthorityDecider.decideStartDraft(command);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenCommandWithNullType_whenDecideStartDraft_thenEventTypeIsNull() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(null, null, null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);

    PriorAuthorityDraftStartedEvent event = PriorAuthorityDecider.decideStartDraft(command);

    assertThat(event.priorAuthorityType()).isNull();
  }

  @Test
  void givenSubmitCommand_whenDecideSubmit_thenAlwaysUsesDataVersionZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    state.applicationId = UUID.randomUUID();
    state.priorAuthorityType = PriorAuthorityType.COUNSEL.name();
    state.schemaVersion = 2;
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    PriorAuthoritySubmittedEvent event = PriorAuthorityDecider.decideSubmit(command, state, 4L);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(state.applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.COUNSEL.name());
    assertThat(event.schemaVersion()).isEqualTo(2);
    assertThat(event.dataVersion()).isEqualTo(0L);
    assertThat(event.applicationDataVersion()).isEqualTo(4L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenSubmittedState_whenDecideDecision_thenReturnsDecisionMadeEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 4L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            4L,
            "GRANTED",
            "Recorded",
            BigDecimal.valueOf(350.0),
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    Optional<PriorAuthorityDecisionMadeEvent> result =
        PriorAuthorityDecider.decideDecision(state, command);

    assertThat(result).isPresent();
    assertThat(result.get().priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(result.get().applicationId()).isEqualTo(applicationId);
    assertThat(result.get().priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(result.get().dataVersion()).isEqualTo(5L);
    assertThat(result.get().overallDecision()).isEqualTo("GRANTED");
  }

  @Test
  void givenDecidedStatusWithSameDecisionAndPayload_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.decided = true;
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            1L,
            "REFUSED",
            "Recorded",
            BigDecimal.ZERO,
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"REFUSED\"}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  @Test
  void givenUnsupportedDecisionValue_whenDecideDecision_thenThrowsValidationException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 0L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "PART_GRANTED",
            "Recorded",
            BigDecimal.ZERO,
            null,
            null,
            null,
            OCCURRED_AT,
            "{}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void givenSubmittedStateWithDifferentVersion_whenDecideDecision_thenThrowsVersionConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 3L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            2L,
            "GRANTED",
            "Recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(PriorAuthorityVersionConflictException.class);
  }

  @Test
  void givenAlreadyDecidedWithDifferentPayload_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.decided = true;
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            1L,
            "REFUSED",
            "Recorded",
            BigDecimal.ZERO,
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"REFUSED\"}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  @Test
  void givenDraftState_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = draftState(priorAuthorityId, applicationId);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            0L,
            "GRANTED",
            "Recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  @Test
  void
      givenAlreadyDecidedWithNullDecisionSerialisedRequest_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.decided = true;
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            TestJwtDecoderConfig.CASEWORKER_ID,
            1L,
            "GRANTED",
            "Recorded",
            BigDecimal.valueOf(100.0),
            null,
            null,
            null,
            OCCURRED_AT,
            "{\"decision\":\"GRANTED\"}",
            OCCURRED_AT);

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(PriorAuthorityStatusConflictException.class);
  }

  private static PriorAuthorityState submittedState(
      UUID priorAuthorityId, UUID applicationId, long dataVersion) {
    PriorAuthorityState state = new PriorAuthorityState();
    state.priorAuthorityId = priorAuthorityId;
    state.applicationId = applicationId;
    state.priorAuthorityType = PriorAuthorityType.EXPERT.name();
    state.schemaVersion = 1;
    state.dataVersion = dataVersion;
    state.caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    state.submitted = true;
    return state;
  }

  private static PriorAuthorityState draftState(UUID priorAuthorityId, UUID applicationId) {
    PriorAuthorityState state = new PriorAuthorityState();
    state.priorAuthorityId = priorAuthorityId;
    state.applicationId = applicationId;
    state.priorAuthorityType = PriorAuthorityType.EXPERT.name();
    state.schemaVersion = 1;
    state.dataVersion = 0L;
    state.caseworkerId = TestJwtDecoderConfig.CASEWORKER_ID;
    state.submitted = false;
    return state;
  }

  private static PriorAuthorityDataPayload payload(UUID priorAuthorityId, UUID applicationId) {
    return new PriorAuthorityDataPayload(
        priorAuthorityId,
        applicationId,
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, null, null, null, null),
        "{}",
        OCCURRED_AT,
        new PriorAuthorityDataPayload.DecisionDetails(
            "GRANTED",
            "Recorded",
            BigDecimal.ZERO,
            OCCURRED_AT,
            null,
            null,
            null,
            "{\"decision\":\"GRANTED\"}"));
  }

  @Test
  void givenUploadCommand_whenDecideDocumentUploaded_thenMapsUploadFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            "CIVIL_APPLY",
            "abc123",
            "{}",
            OCCURRED_AT,
            "evidence.pdf",
            7L,
            "PDF",
            "application/pdf");
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityDocumentUploadedEvent event =
        PriorAuthorityDecider.decideDocumentUploaded(command, applicationId);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.documentId()).isEqualTo(documentId);
    assertThat(event.uploadedAt()).isEqualTo(OCCURRED_AT);
    assertThat(event.size()).isEqualTo(7L);
    assertThat(event.contentType()).isEqualTo("application/pdf");
    assertThat(event.checksum()).isEqualTo("abc123");
    assertThat(event.parentApplicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenDeleteCommand_whenDecideDocumentDeleted_thenMapsDeleteFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, "{}", OCCURRED_AT);
    UUID applicationId = UUID.randomUUID();

    PriorAuthorityDocumentDeletedEvent event =
        PriorAuthorityDecider.decideDocumentDeleted(command, applicationId);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.documentId()).isEqualTo(documentId);
    assertThat(event.deletedAt()).isEqualTo(OCCURRED_AT);
    assertThat(event.parentApplicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenDocumentTypeUpdateCommand_whenDecideDocumentTypeUpdated_thenMapsUpdateFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "{}", OCCURRED_AT);

    PriorAuthorityDocumentTypeUpdatedEvent event =
        PriorAuthorityDecider.decideDocumentTypeUpdated(command);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.documentId()).isEqualTo(documentId);
    assertThat(event.documentType()).isEqualTo("GATEWAY_EVIDENCE");
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenUpdateCommand_whenDecideDraftUpdated_thenMapsPointerFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UpdatePriorAuthorityDraftCommand command =
        new UpdatePriorAuthorityDraftCommand(
            priorAuthorityId,
            new PriorAuthorityContent(null, "need expert", null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);

    PriorAuthorityDraftUpdatedEvent event =
        PriorAuthorityDecider.decideDraftUpdated(command, applicationId);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.parentApplicationId()).isEqualTo(applicationId);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
