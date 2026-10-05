package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.SubmitPriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;

@ExtendWith(MockitoExtension.class)
class SubmitPriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private JsonSchemaValidator jsonSchemaValidator;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenExistingDraft_whenHandle_thenValidatesSchemaStoresDataAndEmitsSubmittedEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT, "Need urgent review", null, null, null);
    PriorAuthorityDataPayload payload =
        new PriorAuthorityDataPayload(priorAuthorityId, applicationId, content, "{}", OCCURRED_AT);
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(payload));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);
    when(applicationDataStore.latestVersion(applicationId)).thenReturn(3L);

    new SubmitPriorAuthorityDraftCommandHandler()
        .handle(
            command,
            draftStore,
            dataStore,
            applicationDataStore,
            jsonSchemaValidator,
            priorAuthority,
            eventAppender);

    verify(jsonSchemaValidator).validate(content, "PriorAuthority.json", 1);
    verify(dataStore)
        .append(
            eq(priorAuthorityId),
            eq(0L),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{}"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue()).isEqualTo(payload);
    verify(eventAppender)
        .append(
            new PriorAuthoritySubmittedEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                1,
                0L,
                3L,
                OCCURRED_AT));
    verify(draftStore).delete(priorAuthorityId);
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    SubmitPriorAuthorityDraftCommand command =
        new SubmitPriorAuthorityDraftCommand(priorAuthorityId, OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new SubmitPriorAuthorityDraftCommandHandler()
                    .handle(
                        command,
                        draftStore,
                        dataStore,
                        applicationDataStore,
                        jsonSchemaValidator,
                        priorAuthority,
                        eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(dataStore, applicationDataStore, jsonSchemaValidator, eventAppender);
  }
}
