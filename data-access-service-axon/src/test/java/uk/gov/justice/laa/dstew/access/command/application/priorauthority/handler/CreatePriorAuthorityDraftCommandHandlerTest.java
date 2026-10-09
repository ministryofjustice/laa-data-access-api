package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.CreatePriorAuthorityDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataStore;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.PriorAuthorityCreationConflictException;
import uk.gov.justice.laa.dstew.access.security.OfficeCodeWriteAccessPolicy;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class CreatePriorAuthorityDraftCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityDataStore dataStore;
  @Mock private ApplicationAggregate application;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private OfficeCodeWriteAccessPolicy writeAccessPolicy;
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
    ApplicationDataPayload applicationData =
        new ApplicationDataPayload(
            null,
            null,
            ApplicationProvider.builder().officeCode("1A001B").build(),
            null,
            OCCURRED_AT,
            null,
            null,
            null,
            null,
            "{}",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(application.isGranted()).thenReturn(true);
    when(draftStore.exists(priorAuthorityId)).thenReturn(false);
    when(dataStore.exists(priorAuthorityId)).thenReturn(false);
    when(applicationDataStore.latestVersion(applicationId)).thenReturn(3L);
    when(applicationDataStore.get(applicationId, 3L)).thenReturn(applicationData);

    new CreatePriorAuthorityDraftCommandHandler()
        .handle(
            command,
            draftStore,
            dataStore,
            application,
            applicationDataStore,
            writeAccessPolicy,
            eventAppender);

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
    assertThat(payloadCaptor.getValue().officeCode()).isEqualTo("1A001B");
    verify(writeAccessPolicy).requireWriteAccess("1A001B");
    verify(eventAppender)
        .append(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId,
                applicationId,
                PriorAuthorityType.EXPERT.name(),
                1,
                OCCURRED_AT,
                "1A001B"));
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
    when(draftStore.exists(priorAuthorityId)).thenReturn(false);
    when(dataStore.exists(priorAuthorityId)).thenReturn(false);

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(
                        command,
                        draftStore,
                        dataStore,
                        application,
                        applicationDataStore,
                        writeAccessPolicy,
                        eventAppender))
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
  void givenGrantedApplicationWithoutProvider_whenHandle_thenCreatesDraftWithNullOfficeCode() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityContent content =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT, "Need urgent review", null, null, null);
    CreatePriorAuthorityDraftCommand command =
        new CreatePriorAuthorityDraftCommand(
            priorAuthorityId, applicationId, content, "{}", 1, "PriorAuthority.json", OCCURRED_AT);
    ApplicationDataPayload applicationData =
        new ApplicationDataPayload(
            null,
            null,
            null,
            null,
            OCCURRED_AT,
            null,
            null,
            null,
            null,
            "{}",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(application.isGranted()).thenReturn(true);
    when(draftStore.exists(priorAuthorityId)).thenReturn(false);
    when(dataStore.exists(priorAuthorityId)).thenReturn(false);
    when(applicationDataStore.latestVersion(applicationId)).thenReturn(7L);
    when(applicationDataStore.get(applicationId, 7L)).thenReturn(applicationData);

    new CreatePriorAuthorityDraftCommandHandler()
        .handle(
            command,
            draftStore,
            dataStore,
            application,
            applicationDataStore,
            writeAccessPolicy,
            eventAppender);

    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("{}"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().officeCode()).isNull();
    verify(writeAccessPolicy).requireWriteAccess(null);
    verify(eventAppender)
        .append(
            new PriorAuthorityDraftStartedEvent(
                priorAuthorityId, applicationId, PriorAuthorityType.EXPERT.name(), 1, OCCURRED_AT));
  }

  @Test
  void givenExistingDraft_whenHandle_thenThrowsPriorAuthorityCreationConflictException() {
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

    when(draftStore.exists(priorAuthorityId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(
                        command,
                        draftStore,
                        dataStore,
                        application,
                        applicationDataStore,
                        writeAccessPolicy,
                        eventAppender))
        .isInstanceOf(PriorAuthorityCreationConflictException.class)
        .hasMessage("Prior authority already exists for submission: " + priorAuthorityId);

    verifyNoInteractions(dataStore, application, applicationDataStore, eventAppender);
  }

  @Test
  void
      givenExistingSubmittedPriorAuthority_whenHandle_thenThrowsPriorAuthorityCreationConflictException() {
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

    when(draftStore.exists(priorAuthorityId)).thenReturn(false);
    when(dataStore.exists(priorAuthorityId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(
                        command,
                        draftStore,
                        dataStore,
                        application,
                        applicationDataStore,
                        writeAccessPolicy,
                        eventAppender))
        .isInstanceOf(PriorAuthorityCreationConflictException.class)
        .hasMessage("Prior authority already exists for submission: " + priorAuthorityId);

    verifyNoInteractions(application, applicationDataStore, eventAppender);
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
    ApplicationDataPayload applicationData =
        new ApplicationDataPayload(
            null,
            null,
            ApplicationProvider.builder().officeCode("1A001B").build(),
            null,
            OCCURRED_AT,
            null,
            null,
            null,
            null,
            "{}",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(application.isGranted()).thenReturn(true);
    when(draftStore.exists(priorAuthorityId)).thenReturn(false);
    when(dataStore.exists(priorAuthorityId)).thenReturn(false);
    when(applicationDataStore.latestVersion(applicationId)).thenReturn(3L);
    when(applicationDataStore.get(applicationId, 3L)).thenReturn(applicationData);
    doThrow(new AccessDeniedException("Caller is not authorised to write this resource"))
        .when(writeAccessPolicy)
        .requireWriteAccess("1A001B");

    assertThatThrownBy(
            () ->
                new CreatePriorAuthorityDraftCommandHandler()
                    .handle(
                        command,
                        draftStore,
                        dataStore,
                        application,
                        applicationDataStore,
                        writeAccessPolicy,
                        eventAppender))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(eventAppender);
    verify(draftStore).exists(priorAuthorityId);
    verify(draftStore, never()).upsert(any(), any(), any(), any(), any());
    verify(dataStore).exists(priorAuthorityId);
  }
}
