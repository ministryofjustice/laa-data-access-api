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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentUploadCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @Captor private ArgumentCaptor<PriorAuthorityDataPayload> payloadCaptor;

  @Test
  void givenExistingDraft_whenHandle_thenAddsUploadedDocumentAndEmitsEvent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityContent existingContent =
        new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null);
    PriorAuthorityDataPayload existingDraft =
        new PriorAuthorityDataPayload(
            priorAuthorityId, applicationId, existingContent, "old", OCCURRED_AT);
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            "service",
            "checksum",
            "new",
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "pdf",
            "application/pdf");

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);

    UUID returnedDocumentId =
        new PriorAuthorityDocumentUploadCommandHandler()
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
        .extracting(PriorAuthorityDocument::documentId)
        .containsExactly(documentId);
    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentUploadedEvent(
                priorAuthorityId,
                documentId,
                OCCURRED_AT,
                1024L,
                "application/pdf",
                "checksum",
                applicationId));
  }

  @Test
  void givenMissingDraft_whenHandle_thenThrowsResourceNotFoundException() {
    UUID priorAuthorityId = UUID.randomUUID();
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            UUID.randomUUID(),
            "service",
            "checksum",
            "new",
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "pdf",
            "application/pdf");

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(priorAuthority, eventAppender);
  }
}
