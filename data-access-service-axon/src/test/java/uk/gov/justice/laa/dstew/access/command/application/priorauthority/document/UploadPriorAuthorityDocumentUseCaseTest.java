package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doNothing;
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
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

@ExtendWith(MockitoExtension.class)
class UploadPriorAuthorityDocumentUseCaseTest {

  @Mock private PriorAuthorityDraftStore draftStore;
  @Mock private RetryingCommandDispatcher dispatcher;
  @Mock private SdsService sdsService;

  @Test
  void givenDraftExists_whenExecute_thenValidatesApplicationAndDispatchesUploadCommand() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
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
    doNothing().when(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    when(sdsService.savePriorAuthorityFile(
            org.mockito.ArgumentMatchers.eq(priorAuthorityId),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq(file)))
        .thenReturn(new DocumentUploadResponse().checksum("abc123"));
    doNothing()
        .when(dispatcher)
        .dispatch(org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class));

    var response = useCase.execute(priorAuthorityId, file);

    assertThat(response.getDocumentId()).isNotNull();
    verify(dispatcher).dispatch(new ValidateApplicationGrantedCommand(applicationId));
    verify(sdsService)
        .savePriorAuthorityFile(
            org.mockito.ArgumentMatchers.eq(priorAuthorityId),
            org.mockito.ArgumentMatchers.eq(response.getDocumentId()),
            org.mockito.ArgumentMatchers.eq(file));
    verify(dispatcher)
        .dispatch(org.mockito.ArgumentMatchers.any(PriorAuthorityDocumentUploadCommand.class));
  }

  @Test
  void givenDraftMissing_whenExecute_thenThrowsNotFound() {
    UploadPriorAuthorityDocumentUseCase useCase =
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
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
        new UploadPriorAuthorityDocumentUseCase(draftStore, dispatcher, sdsService);
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
