package uk.gov.justice.laa.dstew.access.command.application.priorauthority.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityAggregate;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftStartedEvent;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityEvolve;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityState;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthoritySubmittedEvent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityType;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentUploadCommandHandlerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private PriorAuthorityAggregate priorAuthority;
  @Mock private EventAppender eventAppender;

  @ParameterizedTest
  @CsvSource({"file.pdf,.pdf", "file.PDF,.PDF", "file.png,.png", "file,''"})
  void givenExistingDraft_whenHandle_thenAddsUploadedDocumentAndEmitsEvent(
      String filename, String suffix) {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    PriorAuthorityDocumentUploadCommand command =
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            "service",
            "checksum",
            OCCURRED_AT,
            filename,
            1024L,
            "application/pdf");

    when(priorAuthority.getApplicationId()).thenReturn(applicationId);
    when(priorAuthority.getState()).thenReturn(draftState(priorAuthorityId, applicationId));

    UUID returnedDocumentId =
        new PriorAuthorityDocumentUploadCommandHandler()
            .handle(command, priorAuthority, eventAppender);

    assertThat(returnedDocumentId).isEqualTo(documentId);
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
                "service",
                suffix,
                filename));
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

    when(priorAuthority.getState()).thenReturn(new PriorAuthorityState());

    assertThatThrownBy(
            () ->
                new PriorAuthorityDocumentUploadCommandHandler()
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Prior Authority draft not found: " + priorAuthorityId);

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
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(eventAppender);
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
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

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
            priorAuthorityId, applicationId, "EXPERT", 1, 1L, OCCURRED_AT));
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
                    .handle(command, priorAuthority, eventAppender))
        .isInstanceOf(ValidationException.class);

    verifyNoInteractions(eventAppender);
  }

  private static PriorAuthorityState draftState(UUID priorAuthorityId, UUID applicationId) {
    PriorAuthorityState state = new PriorAuthorityState();
    PriorAuthorityEvolve.apply(
        state,
        new PriorAuthorityDraftStartedEvent(
            priorAuthorityId,
            applicationId,
            "EXPERT",
            1,
            OCCURRED_AT,
            "1A001B",
            Erasable.of(
                new PriorAuthorityContent(PriorAuthorityType.EXPERT, "Existing", null, null, null)),
            Erasable.of("{}")));
    return state;
  }
}
