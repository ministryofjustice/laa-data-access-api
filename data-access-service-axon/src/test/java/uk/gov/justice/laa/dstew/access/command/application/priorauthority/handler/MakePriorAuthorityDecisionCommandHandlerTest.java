package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.repository.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ExpertFeeInformation;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.content.priorauthority.TimeRequested;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityStatusConflictException;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityVersionConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class MakePriorAuthorityDecisionCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenSubmittedPriorAuthority_whenHandle_thenPersistsDecisionAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    long dataVersion = 3L;
    PriorAuthorityState state = new PriorAuthorityState();
    setField(state, "applicationId", applicationId);
    setField(state, "caseworkerId", caseworkerId);
    setField(state, "dataVersion", dataVersion);
    setField(state, "submitted", true);
    setField(state, "priorAuthorityType", PriorAuthorityType.EXPERT.name());
    setField(state, "schemaVersion", 1);

    PriorAuthorityContent content =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Need review", null, null, null);
    PriorAuthorityDataPayload current =
        new PriorAuthorityDataPayload(priorAuthorityId, applicationId, content, OCCURRED_AT);
    setField(state, "priorAuthorityId", priorAuthorityId);
    setField(state, "submission", current);
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            caseworkerId,
            dataVersion,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(100.00),
            null,
            null,
            null,
            OCCURRED_AT,
            OCCURRED_AT);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    new MakePriorAuthorityDecisionCommandHandler()
        .handle(command, dataStore, priorAuthority, eventAppender);

    verify(dataStore)
        .append(
            eq(priorAuthorityId),
            eq(dataVersion + 1),
            eq(applicationId),
            payloadCaptor.capture(),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().content()).isEqualTo(content);
    assertThat(payloadCaptor.getValue().decision()).isEqualTo("GRANTED");
    assertThat(payloadCaptor.getValue().decisionJustification()).isEqualTo("Decision recorded");
    assertThat(payloadCaptor.getValue().amountGranted())
        .isEqualByComparingTo(BigDecimal.valueOf(100.00));
    verify(eventAppender)
        .append(
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                dataVersion + 1,
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(100.00),
                OCCURRED_AT,
                OCCURRED_AT,
                null,
                null,
                null));
    verifyNoMoreInteractions(dataStore);
  }

  @Test
  void givenTimeOverride_whenHandle_thenPersistsOverrideInDecisionData() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    long dataVersion = 3L;
    PriorAuthorityState state = new PriorAuthorityState();
    setField(state, "applicationId", applicationId);
    setField(state, "caseworkerId", caseworkerId);
    setField(state, "dataVersion", dataVersion);
    setField(state, "submitted", true);
    setField(state, "priorAuthorityType", PriorAuthorityType.EXPERT.name());
    setField(state, "billingType", "HOURLY");
    setField(state, "schemaVersion", 1);
    PriorAuthorityDataPayload current =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Need review", null, null, null),
            OCCURRED_AT);
    setField(state, "priorAuthorityId", priorAuthorityId);
    setField(state, "submission", current);
    TimeRequested timeRequested = new TimeRequested(3, 15);
    ExpertFeeInformation expertFee =
        ExpertFeeInformation.builder().grantedTime(timeRequested).build();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            caseworkerId,
            dataVersion,
            "GRANTED",
            "Decision recorded",
            BigDecimal.valueOf(100.00),
            expertFee,
            null,
            null,
            OCCURRED_AT,
            OCCURRED_AT);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    new MakePriorAuthorityDecisionCommandHandler()
        .handle(command, dataStore, priorAuthority, eventAppender);

    verify(dataStore)
        .append(
            eq(priorAuthorityId),
            eq(dataVersion + 1),
            eq(applicationId),
            payloadCaptor.capture(),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().expert().getGrantedTime()).isEqualTo(timeRequested);
    verify(eventAppender)
        .append(
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                dataVersion + 1,
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(100.00),
                OCCURRED_AT,
                OCCURRED_AT,
                expertFee,
                null,
                null));
  }

  @Test
  void givenErasedSubmittedContent_whenHandle_thenThrowsNotFoundWithoutWriting() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, caseworkerId, false);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    assertThatExceptionOfType(EntityNotFoundException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(
                        decisionCommand(priorAuthorityId, caseworkerId, 3L),
                        dataStore,
                        priorAuthority,
                        eventAppender));

    verifyNoInteractions(dataStore, eventAppender);
  }

  @Test
  void givenErasedContentAndStaleVersion_whenHandle_thenStillConflicts() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, caseworkerId, false);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    assertThatExceptionOfType(PriorAuthorityVersionConflictException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(
                        decisionCommand(priorAuthorityId, caseworkerId, 2L),
                        dataStore,
                        priorAuthority,
                        eventAppender));

    verifyNoInteractions(dataStore, eventAppender);
  }

  @Test
  void givenErasedContentOnDecidedPriorAuthority_whenHandle_thenStillConflicts() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    PriorAuthorityState state = submittedState(priorAuthorityId, caseworkerId, true);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    assertThatExceptionOfType(PriorAuthorityStatusConflictException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(
                        decisionCommand(priorAuthorityId, caseworkerId, 3L),
                        dataStore,
                        priorAuthority,
                        eventAppender))
        .withMessageContaining("already been decided");

    verifyNoInteractions(dataStore, eventAppender);
  }

  /** A submitted Prior Authority at data version 3 whose submitted content has been erased. */
  private static PriorAuthorityState submittedState(
      UUID priorAuthorityId, UUID caseworkerId, boolean decided) {
    PriorAuthorityState state = new PriorAuthorityState();
    setField(state, "priorAuthorityId", priorAuthorityId);
    setField(state, "applicationId", UUID.randomUUID());
    setField(state, "caseworkerId", caseworkerId);
    setField(state, "dataVersion", 3L);
    setField(state, "submitted", true);
    setField(state, "decided", decided);
    setField(state, "priorAuthorityType", PriorAuthorityType.EXPERT.name());
    return state;
  }

  private static MakePriorAuthorityDecisionCommand decisionCommand(
      UUID priorAuthorityId, UUID caseworkerId, long expectedVersion) {
    return new MakePriorAuthorityDecisionCommand(
        priorAuthorityId,
        caseworkerId,
        expectedVersion,
        "GRANTED",
        "Decision recorded",
        BigDecimal.ONE,
        null,
        null,
        null,
        OCCURRED_AT,
        OCCURRED_AT);
  }

  @Test
  void givenMissingPriorAuthority_whenHandle_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            caseworkerId,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.ZERO,
            null,
            null,
            null,
            OCCURRED_AT,
            OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(null);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(command, dataStore, priorAuthority, eventAppender))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);

    verifyNoInteractions(dataStore, eventAppender);
  }

  @Test
  void givenDifferentPriorAuthorityId_whenHandle_thenThrowsNotFound() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID differentPriorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    MakePriorAuthorityDecisionCommand command =
        new MakePriorAuthorityDecisionCommand(
            priorAuthorityId,
            caseworkerId,
            0L,
            "GRANTED",
            "Decision recorded",
            BigDecimal.ZERO,
            null,
            null,
            null,
            OCCURRED_AT,
            OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(differentPriorAuthorityId);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(command, dataStore, priorAuthority, eventAppender))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);

    verifyNoInteractions(dataStore, eventAppender);
  }

  private static void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Unable to set field " + fieldName, e);
    }
  }
}
