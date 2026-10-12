package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentTypeUpdateCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenExistingDocument_whenHandle_thenUpdatesDocumentTypeAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null);
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            existingContent,
            "old",
            OCCURRED_AT,
            null,
            null,
            Map.of(documentId, "file.pdf"));
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getState())
        .thenReturn(draftStateWithDocument(priorAuthorityId, applicationId, documentId));

    UUID returnedDocumentId =
        new PriorAuthorityDocumentTypeUpdateCommandHandler()
            .handle(command, draftStore, priorAuthority, eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("new"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().documentFilenames()).containsEntry(documentId, "file.pdf");
    assertThat(payloadCaptor.getValue().serialisedRequest()).isEqualTo("new");
    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentTypeUpdatedEvent(
                priorAuthorityId, documentId, "GATEWAY_EVIDENCE", OCCURRED_AT));
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenDraftWithoutRequestedDocument_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null);
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, "old", OCCURRED_AT);
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "Document %s not found for Prior Authority %s".formatted(documentId, priorAuthorityId));

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(eventAppender);
  }

  @Test
  void givenSubmittedAggregate_whenHandle_thenRejectsDocumentMutation() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityState state = draftState(priorAuthorityId, applicationId);
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthoritySubmittedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, 1L, null, OCCURRED_AT));
    when(priorAuthority.getState()).thenReturn(state);
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, UUID.randomUUID(), "GATEWAY_EVIDENCE", "new", OCCURRED_AT);

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(draftStore, eventAppender);
  }

  @Test
  void givenDeletedDocument_whenHandle_thenThrowsNotFoundWithoutUpdatingDraft() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityState state = draftStateWithDocument(priorAuthorityId, applicationId, documentId);
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentDeletedEvent(
            priorAuthorityId, documentId, OCCURRED_AT, applicationId));
    PriorAuthorityDataPayload draft =
        new PriorAuthorityDataPayload(
            priorAuthorityId,
            applicationId,
            new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null),
            "old",
            OCCURRED_AT,
            null,
            null,
            Map.of(documentId, "deleted.pdf"));
    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(draft));
    when(priorAuthority.getState()).thenReturn(state);
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", "new", OCCURRED_AT);

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(draftStore).find(priorAuthorityId);
    verify(draftStore, never())
        .upsert(eq(priorAuthorityId), eq(applicationId), eq(draft), eq("new"), eq(OCCURRED_AT));
    verifyNoInteractions(eventAppender);
  }

  private static PriorAuthorityState draftStateWithDocument(
      UUID priorAuthorityId, UUID applicationId, UUID documentId) {
    PriorAuthorityState state = draftState(priorAuthorityId, applicationId);
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDocumentUploadedEvent(
            priorAuthorityId,
            documentId,
            OCCURRED_AT,
            1024L,
            "application/pdf",
            "checksum",
            applicationId,
            "service"));
    return state;
  }

  private static PriorAuthorityState draftState(UUID priorAuthorityId, UUID applicationId) {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId, applicationId, "EXPERT", 1, OCCURRED_AT));
    return state;
  }
}
