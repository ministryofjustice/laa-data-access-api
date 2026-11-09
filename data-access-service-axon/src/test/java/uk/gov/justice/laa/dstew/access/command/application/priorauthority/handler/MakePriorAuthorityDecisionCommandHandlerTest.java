package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.MakePriorAuthorityDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.decision.PriorAuthorityDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class MakePriorAuthorityDecisionCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Test
  void givenSubmittedPriorAuthority_whenHandle_thenEmitsDecisionWithContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    long dataVersion = 0L;
    PriorAuthorityState state = new PriorAuthorityState();
    setField(state, "applicationId", applicationId);
    setField(state, "caseworkerId", caseworkerId);
    setField(state, "priorAuthorityVersion", dataVersion);
    setField(state, "submitted", true);
    setField(state, "priorAuthorityType", PriorAuthorityType.EXPERT.name());
    setField(state, "schemaVersion", 1);

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
            "{}",
            OCCURRED_AT);

    when(priorAuthority.getState()).thenReturn(state);
    when(priorAuthority.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    new MakePriorAuthorityDecisionCommandHandler().handle(command, priorAuthority, eventAppender);

    verify(eventAppender)
        .append(
            new PriorAuthorityDecisionMadeEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                "GRANTED",
                "Decision recorded",
                BigDecimal.valueOf(100.00),
                OCCURRED_AT,
                OCCURRED_AT));
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
            "{}",
            OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(null);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);

    verifyNoInteractions(eventAppender);
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
            "{}",
            OCCURRED_AT);

    when(priorAuthority.getPriorAuthorityId()).thenReturn(differentPriorAuthorityId);

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(
            () ->
                new MakePriorAuthorityDecisionCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .withMessage("No prior authority found with ID: " + priorAuthorityId);

    verifyNoInteractions(eventAppender);
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
