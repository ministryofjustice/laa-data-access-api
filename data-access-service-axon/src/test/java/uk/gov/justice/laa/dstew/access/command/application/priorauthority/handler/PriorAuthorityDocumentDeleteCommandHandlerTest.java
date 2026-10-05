package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeletedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
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
    PriorAuthorityDocument documentToRemove =
        new PriorAuthorityDocument(
            documentId,
            null,
            "remove.pdf",
            "pdf",
            "application/pdf",
            100L,
            OCCURRED_AT,
            "service",
            "checksum-1");
    PriorAuthorityDocument remainingDocument =
        new PriorAuthorityDocument(
            remainingDocumentId,
            "GATEWAY_EVIDENCE",
            "keep.pdf",
            "pdf",
            "application/pdf",
            200L,
            OCCURRED_AT,
            "service",
            "checksum-2");
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT,
            "Existing",
            null,
            null,
            null,
            List.of(documentToRemove, remainingDocument));
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, "old", OCCURRED_AT);
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);

    UUID returnedDocumentId =
        new PriorAuthorityDocumentDeleteCommandHandler()
            .handle(command, draftStore, priorAuthority, eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("new"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().content().uploadedDocuments())
        .containsExactly(remainingDocument);
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
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentDeleteCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(priorAuthority, eventAppender);
  }

  @Test
  void givenDraftWithoutRequestedDocument_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null, null);
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, "old", OCCURRED_AT);
    PriorAuthorityDocumentDeleteCommand command =
        new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, "new", OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentDeleteCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "Document %s not found for Prior Authority %s".formatted(documentId, priorAuthorityId));

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(priorAuthority, eventAppender);
  }
}
