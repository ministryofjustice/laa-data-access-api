package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeWriteAccessPolicy;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreatePriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityAggregate existing;
  @Mock private ApplicationAggregate application;
  @Mock private OfficeCodeWriteAccessPolicy writeAccessPolicy;
  @Mock private EventAppender eventAppender;

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
    when(application.getOfficeCode()).thenReturn("1A001B");

    new CreatePriorAuthorityDraftCommandHandler()
        .handle(command, null, application, writeAccessPolicy, eventAppender);

    verify(writeAccessPolicy).requireOfficeCodeWriteAccess(eq("1A001B"), isNull());
    verify(eventAppender)
        .append(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                1,
                OCCURRED_AT,
                "1A001B",
                Erasable.of(content),
                Erasable.of("{}")));
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
                    .handle(command, null, application, writeAccessPolicy, eventAppender))
        .isInstanceOf(ValidationException.class)
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly(
                        "Prior authority requires the application to have an overall decision of GRANTED"));

    verify(application).isGranted();
    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenGrantedApplicationWithoutOfficeCode_whenHandle_thenCreatesDraftWithNullOfficeCode() {
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
        .handle(command, null, application, writeAccessPolicy, eventAppender);

    verify(writeAccessPolicy).requireOfficeCodeWriteAccess(isNull(), isNull());
    verify(eventAppender)
        .append(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                1,
                OCCURRED_AT,
                null,
                Erasable.of(content),
                Erasable.of("{}")));
  }

  @Test
  void givenExistingPriorAuthority_whenHandle_thenThrowsPriorAuthorityCreationConflictException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(
                PriorAuthorityType.EXPERT, "Need urgent review", null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);

    when(existing.getPriorAuthorityId()).thenReturn(priorAuthorityId);

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(command, existing, application, writeAccessPolicy, eventAppender))
        .isInstanceOf(PriorAuthorityCreationConflictException.class)
        .hasMessage("Prior authority already exists for submission: " + priorAuthorityId);

    verifyNoInteractions(application, eventAppender);
  }

  @Test
  void givenDeniedParentApplicationOffice_whenHandle_thenDoesNotCreateDraftOrAppendEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(
                PriorAuthorityType.EXPERT, "Need urgent review", null, null, null),
            "{}",
            1,
            "PriorAuthority.json",
            OCCURRED_AT);

    when(application.isGranted()).thenReturn(true);
    when(application.getOfficeCode()).thenReturn("1A001B");
    doThrow(new AccessDeniedException("Caller is not authorised to write this resource"))
        .when(writeAccessPolicy)
        .requireOfficeCodeWriteAccess(eq("1A001B"), isNull());

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(command, null, application, writeAccessPolicy, eventAppender))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(eventAppender);
  }
}
