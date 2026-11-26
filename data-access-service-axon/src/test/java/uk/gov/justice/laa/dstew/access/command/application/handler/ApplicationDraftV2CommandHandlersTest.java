package uk.gov.justice.laa.dstew.access.command.application.handler;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetailsFactory;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationState;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDraftStore;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.JsonSchemaValidator;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class ApplicationDraftV2CommandHandlersTest {

  private static final String BASE_SCHEMA = "BaseCivilApplication.json";

  @Mock private ApplicationDraftStore draftStore;
  @Mock private EventAppender eventAppender;
  @Mock private ApplicationAggregate application;
  @Mock private ApplicationState applicationState;
  @Mock private JsonSchemaValidator jsonSchemaValidator;
  @Mock private ApplicationCreationDetailsFactory detailsFactory;
  @Mock private ApplicationDataStore applicationDataStore;
  @Mock private SubmitApplicationDraftCommandHandler submitDelegate;

  @InjectMocks private CreateApplicationDraftV2CommandHandler createHandler;
  @InjectMocks private UpdateApplicationDraftCommandHandler updateHandler;
  @InjectMocks private SubmitApplicationDraftV2CommandHandler submitHandler;

  @Test
  void createRejectsMissingOfficeCodeWithoutStoringDraft() {
    UUID applicationId = UUID.randomUUID();
    var command =
        new CreateApplicationDraftV2Command(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of("incomplete", true),
            "{}",
            2,
            Instant.now(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> createHandler.handle(command, null, draftStore, eventAppender))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(draftStore, eventAppender);
  }

  @Test
  void createStoresDraftWithMetadataWithoutValidatingContent() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-01-01T10:00:00Z");
    Map<String, Object> content =
        Map.of("provider", Map.of("officeCode", "OFF-1"), "incomplete", true);
    Map<String, Object> metadata = Map.of("currentStep", "client");
    when(draftStore.upsert(any(), any(), any(), any())).thenReturn("fingerprint");
    when(draftStore.findVersion(applicationId)).thenReturn(Optional.of(0L));
    var command =
        new CreateApplicationDraftV2Command(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            content,
            "{}",
            2,
            occurredAt,
            List.of(),
            metadata);

    long version = createHandler.handle(command, null, draftStore, eventAppender);

    ArgumentCaptor<ApplicationDraftPayload> payload =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore).upsert(eq(applicationId), payload.capture(), eq("{}"), eq(occurredAt));
    assertThat(payload.getValue().metadata()).isEqualTo(metadata);
    assertThat(payload.getValue().applicationContent()).isEqualTo(content);
    verifyNoInteractions(jsonSchemaValidator, detailsFactory);
    verify(eventAppender).append(any(ApplicationDraftStartedEvent.class));
    assertThat(version).isZero();
  }

  @Test
  void updateReplacesDraftBodyAndKeepsDocumentFilenames() {
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-01-02T10:00:00Z");
    var existing =
        new ApplicationDraftPayload(
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of("old", true),
            "{}",
            List.of(),
            Map.of(documentId, "evidence.pdf"),
            Map.of("currentStep", "client"));
    when(draftStore.find(applicationId)).thenReturn(Optional.of(existing));
    when(draftStore.findVersion(applicationId)).thenReturn(Optional.of(1L));
    var command =
        new UpdateApplicationDraftCommand(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of("new", true),
            "{\"new\":true}",
            occurredAt,
            List.of(),
            Map.of("currentStep", "proceedings"),
            null);

    long version = updateHandler.handle(command, application, draftStore, eventAppender);

    ArgumentCaptor<ApplicationDraftPayload> payload =
        ArgumentCaptor.forClass(ApplicationDraftPayload.class);
    verify(draftStore).upsert(eq(applicationId), payload.capture(), eq("{\"new\":true}"), any());
    assertThat(payload.getValue().applicationContent()).containsEntry("new", true);
    assertThat(payload.getValue().metadata()).containsEntry("currentStep", "proceedings");
    assertThat(payload.getValue().documentFilenames()).containsEntry(documentId, "evidence.pdf");
    verifyNoInteractions(jsonSchemaValidator);
    verify(eventAppender).append(new ApplicationDraftUpdatedEvent(applicationId, occurredAt));
    assertThat(version).isEqualTo(1L);
  }

  @Test
  void updateWithoutExistingDraftIsNotFound() {
    UUID applicationId = UUID.randomUUID();
    when(draftStore.find(applicationId)).thenReturn(Optional.empty());
    var command =
        new UpdateApplicationDraftCommand(
            applicationId,
            "APPLICATION_IN_PROGRESS",
            "LAA-1",
            Map.of(),
            "{}",
            Instant.now(),
            List.of(),
            Map.of(),
            null);

    assertThatThrownBy(() -> updateHandler.handle(command, application, draftStore, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(draftStore, never()).upsert(any(), any(), any(), any());
    verifyNoInteractions(eventAppender);
  }

  @Test
  void submitRejectsInvalidContentWithoutSubmitting() {
    UUID applicationId = UUID.randomUUID();
    var draft = draftWithContent(Map.of("incomplete", true));
    when(draftStore.find(applicationId)).thenReturn(Optional.of(draft));
    when(application.getState()).thenReturn(applicationState);
    when(applicationState.getSchemaVersion()).thenReturn(2);
    doThrow(new ValidationException(List.of("missing field")))
        .when(jsonSchemaValidator)
        .validate(draft.applicationContent(), BASE_SCHEMA, 2);
    var command = new SubmitApplicationDraftV2Command(applicationId, Instant.now());

    assertThatThrownBy(
            () ->
                submitHandler.handle(
                    command,
                    application,
                    draftStore,
                    detailsFactory,
                    applicationDataStore,
                    eventAppender))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(detailsFactory, applicationDataStore, eventAppender);
  }

  @Test
  void submitValidatesStoredContentAgainstSchemaVersionThenDelegates() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-01-03T10:00:00Z");
    var draft = draftWithContent(Map.of("complete", true));
    when(draftStore.find(applicationId)).thenReturn(Optional.of(draft));
    when(application.getState()).thenReturn(applicationState);
    when(applicationState.getSchemaVersion()).thenReturn(2);
    var command = new SubmitApplicationDraftV2Command(applicationId, occurredAt);

    submitHandler.handle(
        command, application, draftStore, detailsFactory, applicationDataStore, eventAppender);

    verify(jsonSchemaValidator).validate(draft.applicationContent(), BASE_SCHEMA, 2);
    verify(submitDelegate)
        .handle(
            new SubmitApplicationDraftCommand(applicationId, occurredAt),
            application,
            draftStore,
            detailsFactory,
            applicationDataStore,
            eventAppender);
  }

  private static ApplicationDraftPayload draftWithContent(Map<String, Object> content) {
    return new ApplicationDraftPayload(
        "APPLICATION_IN_PROGRESS", "LAA-1", content, "{}", List.of(), Map.of(), Map.of());
  }
}
