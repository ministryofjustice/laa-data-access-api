package uk.gov.justice.laa.dstew.access.command.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationMeritsDecision;
import uk.gov.justice.laa.dstew.access.command.application.decision.ApplicationDecisionMadeEvent;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeApplicationDecisionCommand;
import uk.gov.justice.laa.dstew.access.command.application.decision.MakeDecisionProceeding;

@ExtendWith(MockitoExtension.class)
class MakeApplicationDecisionCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-02T10:00:00Z");

  @Mock private ApplicationAggregate application;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<ApplicationDataPayload> payloadCaptor;

  @Test
  void givenGrantedDecisionWithNullMeritsMap_whenHandled_thenPersistsCertificateAndEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationState state = activeManualState(applicationId, caseworkerId, 4L, 7L);
    ApplicationDataPayload current = payloadWithProceeding(applicationId, proceedingId, null);
    Map<String, Object> certificate = Map.of("reference", "CERT-1");
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            caseworkerId,
            4L,
            "GRANTED",
            List.of(new MakeDecisionProceeding(proceedingId, "GRANTED", "reason", "just")),
            certificate,
            "{}",
            "decision made",
            OCCURRED_AT);

    when(application.getApplicationId()).thenReturn(applicationId);
    when(application.getState()).thenReturn(state);
    when(applicationDataStore.get(applicationId, 7L)).thenReturn(current);

    new MakeApplicationDecisionCommandHandler()
        .handle(command, application, applicationDataStore, eventAppender);

    verify(applicationDataStore)
        .append(eq(applicationId), eq(8L), payloadCaptor.capture(), eq("{}"), eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().overallDecision()).isEqualTo("GRANTED");
    assertThat(payloadCaptor.getValue().autoGranted()).isEqualTo(AutoGrantedState.MANUAL);
    assertThat(payloadCaptor.getValue().certificate()).containsEntry("reference", "CERT-1");
    assertThat(payloadCaptor.getValue().meritsDecisions()).containsKey(proceedingId);
    verify(eventAppender)
        .append(
            new ApplicationDecisionMadeEvent(
                applicationId, 5L, 8L, "GRANTED", AutoGrantedState.MANUAL, OCCURRED_AT));
  }

  @Test
  void
      givenRefusedDecisionWithExistingMerits_whenRecorded_thenKeepsExistingMeritsAndClearsCertificate() {
    UUID applicationId = UUID.randomUUID();
    UUID existingProceedingId = UUID.randomUUID();
    UUID newProceedingId = UUID.randomUUID();
    ApplicationState state = activeManualState(applicationId, UUID.randomUUID(), 1L, 2L);
    ApplicationDataPayload current =
        payloadWithProceeding(
            applicationId,
            newProceedingId,
            Map.of(
                existingProceedingId,
                new ApplicationMeritsDecision("GRANTED", "existing reason", "existing just")));
    MakeApplicationDecisionCommand command =
        new MakeApplicationDecisionCommand(
            applicationId,
            state.getCaseworkerId(),
            1L,
            "REFUSED",
            List.of(
                new MakeDecisionProceeding(newProceedingId, "REFUSED", "new reason", "new just")),
            Map.of("reference", "CERT-2"),
            "{}",
            "decision made",
            OCCURRED_AT);

    MakeApplicationDecisionCommandHandler.recordDecision(
        command,
        AutoGrantedState.MANUAL,
        current,
        applicationWithState(applicationId, state),
        applicationDataStore,
        eventAppender);

    verify(applicationDataStore)
        .append(eq(applicationId), eq(3L), payloadCaptor.capture(), eq("{}"), eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().certificate()).isNull();
    assertThat(payloadCaptor.getValue().meritsDecisions())
        .containsKey(existingProceedingId)
        .containsKey(newProceedingId);
    assertThat(payloadCaptor.getValue().meritsDecisions().get(newProceedingId).decision())
        .isEqualTo("REFUSED");
    verify(eventAppender)
        .append(
            new ApplicationDecisionMadeEvent(
                applicationId, 2L, 3L, "REFUSED", AutoGrantedState.MANUAL, OCCURRED_AT));
  }

  private static ApplicationAggregate applicationWithState(
      UUID applicationId, ApplicationState state) {
    ApplicationAggregate aggregate = org.mockito.Mockito.mock(ApplicationAggregate.class);
    when(aggregate.getState()).thenReturn(state);
    return aggregate;
  }

  private static ApplicationState activeManualState(
      UUID applicationId, UUID caseworkerId, long applicationVersion, long dataVersion) {
    ApplicationState state = new ApplicationState();
    setField(state, "applicationId", applicationId);
    setField(state, "autoGranted", AutoGrantedState.MANUAL);
    setField(state, "caseworkerId", caseworkerId);
    setField(state, "applicationVersion", applicationVersion);
    setField(state, "applicationDataVersion", dataVersion);
    return state;
  }

  private static ApplicationDataPayload payloadWithProceeding(
      UUID applicationId, UUID proceedingId, Map<UUID, ApplicationMeritsDecision> meritsDecisions) {
    ApplicationDataPayload base =
        ApplicationDataPayload.from(applicationCreationDetails(applicationId));
    return new ApplicationDataPayload(
        base.laaReference(),
        base.client(),
        base.provider(),
        base.opponents(),
        base.submittedAt(),
        base.usedDelegatedFunctions(),
        base.categoryOfLaw(),
        base.matterType(),
        List.of(
            Proceeding.builder()
                .id(proceedingId)
                .leadProceeding(true)
                .description("Care order")
                .code("SE003")
                .build()),
        base.serialisedRequest(),
        base.overallDecision(),
        base.autoGranted(),
        meritsDecisions,
        base.certificate(),
        base.decisionSerialisedRequest(),
        base.decisionEventDescription(),
        base.assignmentEventDescription(),
        base.notes(),
        base.documentFilenames());
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
