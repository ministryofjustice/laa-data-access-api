package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.BillingType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

/** Unit tests for {@link PriorAuthorityDecider}. */
class PriorAuthorityDeciderTest {

  private static final AuthenticatedCaller CALLER =
      new AuthenticatedCaller(Optional.empty(), Set.of(), Set.of("1A001B"), Set.of());

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
            OCCURRED_AT,
            CALLER);
    PriorAuthorityDraftStartedEvent event =
        PriorAuthorityDecider.decideStartDraft(command, "1A001B");

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.EXPERT.name());
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(event.officeCode()).isEqualTo("1A001B");
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
            OCCURRED_AT,
            CALLER);

    PriorAuthorityDraftStartedEvent event = PriorAuthorityDecider.decideStartDraft(command, null);

    assertThat(event.priorAuthorityType()).isNull();
    assertThat(event.officeCode()).isNull();
  }

  @Test
  void givenSubmitCommand_whenDecideSubmit_thenAlwaysUsesDataVersionZero() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityState state = new PriorAuthorityState();
    state.applicationId = UUID.randomUUID();
    state.priorAuthorityType = PriorAuthorityType.COUNSEL.name();
    state.schemaVersion = 2;
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT, CALLER);

    PriorAuthoritySubmittedEvent event =
        PriorAuthorityDecider.decideSubmit(command, state, 4L, "FIXED_RATE");

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.applicationId()).isEqualTo(state.applicationId);
    assertThat(event.priorAuthorityType()).isEqualTo(PriorAuthorityType.COUNSEL.name());
    assertThat(event.schemaVersion()).isEqualTo(2);
    assertThat(event.dataVersion()).isZero();
    assertThat(event.applicationDataVersion()).isEqualTo(4L);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(event.billingType()).isEqualTo("FIXED_RATE");
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
  void givenHourlyExpert_whenDecideDecisionWithTimeOverride_thenReturnsEvent() {
    PriorAuthorityState state = submittedState(UUID.randomUUID(), UUID.randomUUID(), 4L);
    state.billingType = BillingType.HOURLY.name();
    MakePriorAuthorityDecisionCommand command =
        decisionCommand(
            state,
            ExpertFeeInformation.builder().newTimeRequested(new TimeRequested(3, 15)).build());

    Optional<PriorAuthorityDecisionMadeEvent> result =
        PriorAuthorityDecider.decideDecision(state, command);

    assertThat(result).isPresent();
  }

  @ParameterizedTest
  @CsvSource(
      value = {"EXPERT,FIXED_RATE", "EXPERT,NULL", "DISBURSEMENT,NULL", "COUNSEL,NULL"},
      nullValues = "NULL")
  void givenNonHourlyExpert_whenDecideDecisionWithTimeOverride_thenThrowsValidation(
      String priorAuthorityType, String billingType) {
    PriorAuthorityState state = submittedState(UUID.randomUUID(), UUID.randomUUID(), 4L);
    state.priorAuthorityType = priorAuthorityType;
    state.billingType = billingType;
    MakePriorAuthorityDecisionCommand command =
        decisionCommand(
            state,
            ExpertFeeInformation.builder().newTimeRequested(new TimeRequested(3, 15)).build());

    assertThatThrownBy(() -> PriorAuthorityDecider.decideDecision(state, command))
        .isInstanceOf(ValidationException.class)
        .extracting(ValidationException.class::cast)
        .extracting(ValidationException::errors)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(
            "expert.newTimeRequested is only permitted for EXPERT prior authorities with HOURLY billing");
  }

  @Test
  void givenFixedRateExpertWithoutTimeOverride_whenDecideDecision_thenReturnsEvent() {
    PriorAuthorityState state = submittedState(UUID.randomUUID(), UUID.randomUUID(), 4L);
    state.billingType = BillingType.FIXED_RATE.name();
    MakePriorAuthorityDecisionCommand command =
        decisionCommand(
            state, ExpertFeeInformation.builder().newFixedRateAmount(BigDecimal.TEN).build());

    Optional<PriorAuthorityDecisionMadeEvent> result =
        PriorAuthorityDecider.decideDecision(state, command);

    assertThat(result).isPresent();
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
        .isInstanceOf(PriorAuthorityStatusConflictException.class)
        .hasMessageContaining("Prior authority has already been decided");
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
  void givenDecidedStateWithSubmittedFalse_whenDecideDecision_thenThrowsDecidedConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = draftState(priorAuthorityId, applicationId);
    state.decided = true;
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
        .isInstanceOf(PriorAuthorityStatusConflictException.class)
        .hasMessageContaining("Prior authority has already been decided");
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

  @Test
  void givenSubmittedStateWithoutAssignment_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    state.caseworkerId = null;
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
        .isInstanceOf(PriorAuthorityStatusConflictException.class)
        .hasMessageContaining("unassigned");
  }

  @Test
  void givenSubmittedStateAssignedToDifferentCaseworker_whenDecideDecision_thenThrowsConflict() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, applicationId, 1L);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            UUID.randomUUID(),
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
        .isInstanceOf(PriorAuthorityStatusConflictException.class)
        .hasMessageContaining("different caseworker");
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

  private static MakePriorAuthorityDecisionCommand decisionCommand(
      PriorAuthorityState state, ExpertFeeInformation expertFee) {
    return new MakePriorAuthorityDecisionCommand(
        state.priorAuthorityId,
        TestJwtDecoderConfig.CASEWORKER_ID,
        state.dataVersion,
        "GRANTED",
        "Recorded",
        BigDecimal.valueOf(350.0),
        expertFee,
        null,
        null,
        OCCURRED_AT,
        "{\"decision\":\"GRANTED\"}",
        OCCURRED_AT);
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
            OCCURRED_AT,
            "evidence.pdf",
            7L,
            "application/pdf",
            CALLER);
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
    assertThat(event.sourceService()).isEqualTo("CIVIL_APPLY");
    assertThat(event.toString()).doesNotContain("evidence.pdf");
  }

  @Test
  void givenDeleteCommand_whenDecideDocumentDeleted_thenMapsDeleteFields() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, OCCURRED_AT, CALLER);
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
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "{}", OCCURRED_AT, CALLER);

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
            OCCURRED_AT,
            CALLER);

    PriorAuthorityDraftUpdatedEvent event =
        PriorAuthorityDecider.decideDraftUpdated(command, applicationId);

    assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(event.parentApplicationId()).isEqualTo(applicationId);
    assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
  }
}
