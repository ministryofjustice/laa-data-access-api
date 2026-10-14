package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdateDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.UpdateApplicationCommand;

@ExtendWith(MockitoExtension.class)
class UpdateApplicationCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-02T11:00:00Z");

  @Mock private ApplicationAggregate application;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private ApplicationUpdateDetailsFactory detailsFactory;
  @Mock private EventAppender eventAppender;
  @Mock private ApplicationDataPayload current;
  @Mock private ApplicationDataPayload updated;

  @Test
  void givenNullStatusCommand_whenHandled_thenPreservesCurrentStatusAndNotEnteringSubmitted() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = state(applicationId, "APPLICATION_SUBMITTED", 2L, 5L);
    UpdateApplicationCommand command =
        new UpdateApplicationCommand(applicationId, null, Map.of(), "{}", OCCURRED_AT);

    stubApplication(applicationId, state);
    when(applicationDataStore.get(applicationId, 5L)).thenReturn(current);
    when(detailsFactory.prepare(command, current, false)).thenReturn(updated);

    new UpdateApplicationCommandHandler()
        .handle(command, application, applicationDataStore, detailsFactory, eventAppender);

    verify(detailsFactory).prepare(command, current, false);
    verify(applicationDataStore)
        .append(eq(applicationId), eq(6L), eq(updated), eq("{}"), eq(OCCURRED_AT));
    verify(eventAppender)
        .append(
            new ApplicationUpdatedEvent(
                applicationId,
                3L,
                6L,
                "APPLICATION_SUBMITTED",
                "APPLICATION_SUBMITTED",
                OCCURRED_AT));
  }

  @Test
  void givenSubmittedStatusFromInProgress_whenHandled_thenMarksEnteringSubmitted() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = state(applicationId, "APPLICATION_IN_PROGRESS", 4L, 7L);
    UpdateApplicationCommand command =
        new UpdateApplicationCommand(
            applicationId, "APPLICATION_SUBMITTED", Map.of(), "{}", OCCURRED_AT);

    stubApplication(applicationId, state);
    when(applicationDataStore.get(applicationId, 7L)).thenReturn(current);
    when(detailsFactory.prepare(command, current, true)).thenReturn(updated);

    new UpdateApplicationCommandHandler()
        .handle(command, application, applicationDataStore, detailsFactory, eventAppender);

    verify(detailsFactory).prepare(command, current, true);
    verify(applicationDataStore)
        .append(eq(applicationId), eq(8L), eq(updated), eq("{}"), eq(OCCURRED_AT));
    verify(eventAppender)
        .append(
            new ApplicationUpdatedEvent(
                applicationId,
                5L,
                8L,
                "APPLICATION_IN_PROGRESS",
                "APPLICATION_SUBMITTED",
                OCCURRED_AT));
  }

  @Test
  void givenDifferentStatusFromInProgress_whenHandled_thenDoesNotMarkEnteringSubmitted() {
    UUID applicationId = UUID.randomUUID();
    ApplicationState state = state(applicationId, "APPLICATION_IN_PROGRESS", 1L, 2L);
    UpdateApplicationCommand command =
        new UpdateApplicationCommand(
            applicationId, "APPLICATION_GRANTED", Map.of(), "{}", OCCURRED_AT);

    stubApplication(applicationId, state);
    when(applicationDataStore.get(applicationId, 2L)).thenReturn(current);
    when(detailsFactory.prepare(command, current, false)).thenReturn(updated);

    new UpdateApplicationCommandHandler()
        .handle(command, application, applicationDataStore, detailsFactory, eventAppender);

    verify(detailsFactory).prepare(command, current, false);
    verify(applicationDataStore)
        .append(eq(applicationId), eq(3L), eq(updated), eq("{}"), eq(OCCURRED_AT));
    verify(eventAppender)
        .append(
            new ApplicationUpdatedEvent(
                applicationId,
                2L,
                3L,
                "APPLICATION_IN_PROGRESS",
                "APPLICATION_GRANTED",
                OCCURRED_AT));
  }

  private void stubApplication(UUID applicationId, ApplicationState state) {
    when(application.getApplicationId()).thenReturn(applicationId);
    when(application.getState()).thenReturn(state);
  }

  private static ApplicationState state(
      UUID applicationId, String status, long applicationVersion, long dataVersion) {
    ApplicationState state = new ApplicationState();
    setField(state, "applicationId", applicationId);
    setField(state, "status", status);
    setField(state, "applicationVersion", applicationVersion);
    setField(state, "applicationDataVersion", dataVersion);
    return state;
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
