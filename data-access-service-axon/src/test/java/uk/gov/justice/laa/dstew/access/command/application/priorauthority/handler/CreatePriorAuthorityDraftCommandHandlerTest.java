package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreatePriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private ApplicationAggregate application;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenGrantedApplication_whenHandle_thenCreatesDraftAndEmitsStartedEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT, "Need urgent review", null, null, null);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, content, "{}", 1, "PriorAuthority.json", OCCURRED_AT);

    when(application.isGranted()).thenReturn(true);

    new CreatePriorAuthorityDraftCommandHandler()
        .handle(command, draftStore, application, eventAppender);

    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{}"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().priorAuthorityId()).isEqualTo(priorAuthorityId);
    assertThat(payloadCaptor.getValue().applicationId()).isEqualTo(applicationId);
    assertThat(payloadCaptor.getValue().content()).isEqualTo(content);
    assertThat(payloadCaptor.getValue().serialisedRequest()).isEqualTo("{}");
    assertThat(payloadCaptor.getValue().submittedAt()).isEqualTo(OCCURRED_AT);
    verify(eventAppender)
        .append(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, PriorAuthorityType.EXPERT.name(), 1, OCCURRED_AT));
  }

  @Test
  void givenUngrantedApplication_whenHandle_thenThrowsValidationException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT, "Need urgent review", null, null, null);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, content, "{}", 1, "PriorAuthority.json", OCCURRED_AT);

    when(application.isGranted()).thenReturn(false);

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(command, draftStore, application, eventAppender))
        .isInstanceOf(ValidationException.class)
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly(
                        "Prior authority requires the application to have an overall decision of GRANTED"));

    verify(application).isGranted();
    verifyNoInteractions(draftStore, eventAppender);
  }
}
