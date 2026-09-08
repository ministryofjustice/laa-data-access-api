package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.exception.InvalidApplicationStateException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class UploadPriorAuthorityDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private RetryingCommandDispatcher dispatcher;

  @Test
  void givenDraftExists_whenExecute_thenValidatesApplicationAndDispatchesUploadCommand() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());

    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(null, null, null, null, null),
                    "{}",
                    java.time.Instant.now())));
    doNothing().when(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    doReturn(documentId)
        .when(dispatcher)
        .dispatch(
            org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class),
            org.mockito.ArgumentMatchers.eq(UUID.class));

    var response = useCase.execute(priorAuthorityId, file);

    assertThat(response.getDocumentId()).isEqualTo(documentId);
    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verify(dispatcher)
        .dispatch(
            org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class),
            org.mockito.ArgumentMatchers.eq(UUID.class));
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsNotFound() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());

    when(draftStore.find(priorAuthorityId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, file));
  }

  @Test
  void givenApplicationNotGranted_whenExecute_thenThrowsAndDoesNotDispatchUploadCommand() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher);
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "content".getBytes());

    when(draftStore.find(priorAuthorityId))
        .thenReturn(
            Optional.of(
                new PriorAuthorityDataPayload(
                    priorAuthorityId,
                    applicationId,
                    new PriorAuthorityContent(null, null, null, null, null),
                    "{}",
                    java.time.Instant.now())));
    doThrow(new InvalidApplicationStateException(applicationId, "IN_PROGRESS"))
        .when(dispatcher)
        .dispatch(new ValidateApplicationGrantedCommand(applicationId));

    assertThatExceptionOfType(InvalidApplicationStateException.class)
        .isThrownBy(() -> useCase.execute(priorAuthorityId, file));

    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verifyNoMoreInteractions(dispatcher);
  }
}
