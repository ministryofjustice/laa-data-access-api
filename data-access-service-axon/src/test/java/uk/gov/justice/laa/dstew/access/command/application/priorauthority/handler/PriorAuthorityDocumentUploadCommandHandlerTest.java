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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
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
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "application/pdf");

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    UUID returnedDocumentId =
        new PriorAuthorityDocumentUploadCommandHandler()
            .handle(command, draftStore, priorAuthority, eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("old"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().documentFilenames()).containsEntry(documentId, "file.pdf");
    verify(eventAppender)
        .append(
            new PriorAuthorityDocumentUploadedEvent(
                priorAuthorityId,
                documentId,
                OCCURRED_AT,
                1024L,
                "application/pdf",
                "checksum",
                applicationId,
                "service"));
  }

  @Test
  void givenDraftWithExistingFilenames_whenHandle_thenPreservesThemAndAddsNewFilename() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID existingDocumentId = UUID.randomUUID();
    UUID newDocumentId = UUID.randomUUID();
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
            "1A001B",
            Map.of(existingDocumentId, "existing.pdf"));
    final PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            newDocumentId,
            "service",
            "checksum",
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "application/pdf");

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.of(existingDraft));
    when(priorAuthority.getApplicationId()).thenReturn(applicationId);
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    new PriorAuthorityDocumentUploadCommandHandler()
        .handle(command, draftStore, priorAuthority, eventAppender);

    verify(draftStore)
        .upsert(
            eq(priorAuthorityId),
            eq(applicationId),
            payloadCaptor.capture(),
            eq("old"),
            eq(OCCURRED_AT));
    assertThat(payloadCaptor.getValue().documentFilenames())
        .containsEntry(existingDocumentId, "existing.pdf")
        .containsEntry(newDocumentId, "file.pdf");
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
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "application/pdf");

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, UUID.randomUUID()));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

    verify(draftStore).find(priorAuthorityId);
    verifyNoInteractions(eventAppender);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void givenMissingOriginalFilename_whenHandle_thenRejectsBeforeWriting(String originalFilename) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            UUID.randomUUID(),
            "service",
            "checksum",
            OCCURRED_AT,
            originalFilename,
            1024L,
            "application/pdf");
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(draftStore, eventAppender);
  }

  @Test
  void givenPreviouslyRecordedDocumentId_whenHandle_thenRejectsDuplicate() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
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
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            "service",
            "checksum",
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "application/pdf");
    when(priorAuthority.getState()).thenReturn(state);

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(draftStore, eventAppender);
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
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            UUID.randomUUID(),
            "service",
            "checksum",
            OCCURRED_AT,
            "file.pdf",
            1024L,
            "application/pdf");
    when(priorAuthority.getState()).thenReturn(state);

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, draftStore, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(draftStore, eventAppender);
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
