package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentDeleteCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenExistingDocument_whenHandle_thenRemovesDocumentAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    UUID remainingDocumentId = UUID.randomUUID();
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
            Map.of(documentId, "remove.pdf", remainingDocumentId, "keep.pdf"));
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);
    when(priorAuthority.getState())
        .thenReturn(draftStateWithDocuments(priorAuthorityId, applicationId, documentId, remainingDocumentId));

    DocumentMetadata returnedDocument =
        new PriorAuthorityDocumentDeleteCommandHandler()
            .handle(command, draftStore, priorAuthority, eventAppender);

    assertThat(returnedDocument)
        .isEqualTo(
            new DocumentMetadata(
                documentId, null, OCCURRED_AT, 100L, "application/pdf", "checksum-1", "service", false));
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("old"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().documentFilenames())
        .containsEntry(remainingDocumentId, "keep.pdf")
        .doesNotContainKey(documentId);
    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentDeletedEvent(
                priorAuthorityId, documentId, OCCURRED_AT, applicationId));
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentDeleteCommandHandler()
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
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentDeleteCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "Document %s not found for Prior Authority %s".formatted(documentId, priorAuthorityId));

    verify(draftStore).find(priorAuthorityId);
        verifyNoInteractions(eventAppender);
  }

    private static PriorAuthorityState draftStateWithDocuments(
            UUID priorAuthorityId, UUID applicationId, UUID documentId, UUID remainingDocumentId) {
        PriorAuthorityState state = draftState(priorAuthorityId, applicationId);
        PriorAuthorityEvolve.apply(
                state,
                new PriorAuthorityDocumentUploadedEvent(
                        priorAuthorityId,
                        documentId,
                        OCCURRED_AT,
                        100L,
                        "application/pdf",
                        "checksum-1",
                        applicationId,
                        "service"));
        PriorAuthorityEvolve.apply(
                state,
                new PriorAuthorityDocumentUploadedEvent(
                        priorAuthorityId,
                        remainingDocumentId,
                        OCCURRED_AT,
                        200L,
                        "application/pdf",
                        "checksum-2",
                        applicationId,
                        "service"));
        return state;
    }

    private static PriorAuthorityState draftState(UUID priorAuthorityId, UUID applicationId) {
        PriorAuthorityState state = new PriorAuthorityState();
        PriorAuthorityEvolve.apply(
                state,
                new PriorAuthorityDraftStartedEvent(priorAuthorityId, applicationId, "EXPERT", 1, OCCURRED_AT));
        return state;
    }
}
