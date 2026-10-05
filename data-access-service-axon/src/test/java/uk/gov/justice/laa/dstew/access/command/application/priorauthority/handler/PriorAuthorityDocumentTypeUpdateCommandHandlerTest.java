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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;

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
    PriorAuthorityDocument document =
        new PriorAuthorityDocument(
            documentId,
            null,
            "file.pdf",
            "pdf",
            "application/pdf",
            1024L,
            OCCURRED_AT,
            "service",
            "checksum");
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(
            PriorAuthorityType.EXPERT, "Existing", null, null, null, List.of(document));
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, "old", OCCURRED_AT);
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId,
            documentId,
            PriorAuthorityDocumentType.GATEWAY_EVIDENCE.name(),
            "new",
            OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));

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
    assertThat(payloadCaptor.getValue().content().uploadedDocuments())
        .extracting(PriorAuthorityDocument::documentType)
        .containsExactly(PriorAuthorityDocumentType.GATEWAY_EVIDENCE.name());
    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentTypeUpdatedEvent(
                priorAuthorityId,
                documentId,
                PriorAuthorityDocumentType.GATEWAY_EVIDENCE.name(),
                OCCURRED_AT));
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId,
            documentId,
            PriorAuthorityDocumentType.GATEWAY_EVIDENCE.name(),
            "new",
            OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
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
    PriorAuthorityDocumentTypeUpdateCommand command =
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId,
            documentId,
            PriorAuthorityDocumentType.GATEWAY_EVIDENCE.name(),
            "new",
            OCCURRED_AT);

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentTypeUpdateCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage(
            "Document %s not found for Prior Authority %s".formatted(documentId, priorAuthorityId));

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(priorAuthority, eventAppender);
  }
}
